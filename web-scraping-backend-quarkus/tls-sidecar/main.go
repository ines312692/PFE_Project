package main

import (
	"bufio"
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"sync/atomic"
	"syscall"
	"time"

	utls "github.com/refraction-networking/utls"
	"golang.org/x/net/http2"
)

type config struct {
	listenAddr    string
	upstreamProxy string
	caDir         string
	dialTimeout   time.Duration
	idleTimeout   time.Duration
	helloID       utls.ClientHelloID
}

func loadConfig() config {
	port := getenv("TLS_SIDECAR_PORT", "8888")
	return config{
		listenAddr:    ":" + port,
		upstreamProxy: os.Getenv("UPSTREAM_PROXY"),
		caDir:         getenv("TLS_CA_DIR", "./ca"),
		dialTimeout:   parseDuration("TLS_DIAL_TIMEOUT", 15*time.Second),
		idleTimeout:   parseDuration("TLS_IDLE_TIMEOUT", 90*time.Second),
		helloID:       utls.HelloChrome_120,
	}
}

type metrics struct {
	requestsTotal   atomic.Uint64
	requestsActive  atomic.Int64
	httpsHandshakes atomic.Uint64
	handshakeErrors atomic.Uint64
	upstreamErrors  atomic.Uint64
	h2Upstream      atomic.Uint64
	h1Upstream      atomic.Uint64
	bytesIn         atomic.Uint64
	bytesOut        atomic.Uint64
	startedAt       time.Time
}

var m = &metrics{startedAt: time.Now()}

func (mm *metrics) snapshot() map[string]any {
	return map[string]any{
		"requests_total":   mm.requestsTotal.Load(),
		"requests_active":  mm.requestsActive.Load(),
		"https_handshakes": mm.httpsHandshakes.Load(),
		"handshake_errors": mm.handshakeErrors.Load(),
		"upstream_errors":  mm.upstreamErrors.Load(),
		"upstream_h2":      mm.h2Upstream.Load(),
		"upstream_h1":      mm.h1Upstream.Load(),
		"bytes_in":         mm.bytesIn.Load(),
		"bytes_out":        mm.bytesOut.Load(),
		"uptime_seconds":   int64(time.Since(mm.startedAt).Seconds()),
		"impersonating":    "chrome_120",
	}
}

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	slog.SetDefault(logger)

	cfg := loadConfig()

	ca, err := newCAManager(cfg.caDir)
	if err != nil {
		slog.Error("CA init failed", "err", err)
		os.Exit(1)
	}

	slog.Info("tls-sidecar starting",
		"listen", cfg.listenAddr,
		"upstream_proxy", maskProxy(cfg.upstreamProxy),
		"ca_cert", ca.caPEMPath,
		"impersonating", "chrome_120",
		"mode", "mitm-h2")

	srv := &http.Server{
		Addr:        cfg.listenAddr,
		Handler:     newHandler(cfg, ca),
		IdleTimeout: cfg.idleTimeout,
	}

	stopCtx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go func() {
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("server error", "err", err)
			os.Exit(1)
		}
	}()

	<-stopCtx.Done()
	slog.Info("shutdown signal received, draining")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = srv.Shutdown(shutdownCtx)
	slog.Info("bye")
}

func newHandler(cfg config, ca *caManager) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", handleHealth)
	mux.HandleFunc("/metrics", handleMetrics)
	mux.HandleFunc("/fingerprint", handleFingerprint(cfg))
	proxy := &proxyHandler{
		cfg: cfg,
		ca:  ca,
		rt:  newUpstreamRoundTripper(cfg),
	}
	return &routedHandler{mux: mux, proxy: proxy}
}

type routedHandler struct {
	mux   *http.ServeMux
	proxy http.Handler
}

func (r *routedHandler) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	if req.Method == http.MethodConnect || req.URL.IsAbs() {
		r.proxy.ServeHTTP(w, req)
		return
	}
	r.mux.ServeHTTP(w, req)
}

func handleHealth(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "up"})
}

func handleMetrics(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, m.snapshot())
}

func handleFingerprint(cfg config) http.HandlerFunc {
	rt := newUpstreamRoundTripper(cfg)
	return func(w http.ResponseWriter, req *http.Request) {
		out := map[string]any{
			"client_hello": "HelloChrome_120",
			"alpn_offered": []string{"h2", "http/1.1"},
			"min_tls":      "1.2",
			"max_tls":      "1.3",
			"note":         "JA3/JA4 derived from the uTLS Chrome_120 preset",
		}
		if target := req.URL.Query().Get("url"); target != "" {
			if u, err := url.Parse(target); err == nil && u.Hostname() != "" {
				ctx, cancel := context.WithTimeout(req.Context(), cfg.dialTimeout)
				defer cancel()
				conn, alpn, err := rt.dialChromeTLS(ctx, u.Hostname())
				if err != nil {
					out["probe_error"] = err.Error()
				} else {
					_ = conn.Close()
					out["origin"] = u.Hostname()
					out["origin_alpn"] = alpn
				}
			}
		}
		writeJSON(w, http.StatusOK, out)
	}
}

type proxyHandler struct {
	cfg config
	ca  *caManager
	rt  *upstreamRoundTripper
}

func (p *proxyHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	m.requestsTotal.Add(1)
	m.requestsActive.Add(1)
	defer m.requestsActive.Add(-1)

	if r.Method == http.MethodConnect {
		p.handleConnect(w, r)
		return
	}
	p.handlePlainHTTP(w, r)
}

func (p *proxyHandler) handleConnect(w http.ResponseWriter, r *http.Request) {
	target := r.Host
	host, port, err := net.SplitHostPort(target)
	if err != nil {
		http.Error(w, "bad CONNECT target", http.StatusBadRequest)
		return
	}
	if port != "443" {
		p.tunnelRaw(w, r, target)
		return
	}

	hijacker, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "hijacking not supported", http.StatusInternalServerError)
		return
	}
	clientRaw, clientBuf, err := hijacker.Hijack()
	if err != nil {
		slog.Error("hijack failed", "err", err)
		return
	}
	defer clientRaw.Close()

	if err := writeConnectResponse(clientBuf, http.StatusOK, "Connection established"); err != nil {
		slog.Debug("client write failed", "err", err)
		return
	}

	leaf, err := p.ca.leafFor(host)
	if err != nil {
		slog.Warn("leaf cert mint failed", "host", host, "err", err)
		return
	}

	clientTLSCfg := &tls.Config{
		Certificates: []tls.Certificate{leaf.cert},
		MinVersion:   tls.VersionTLS12,
		NextProtos:   []string{"h2", "http/1.1"},
	}
	clientTLS := tls.Server(clientRaw, clientTLSCfg)
	_ = clientRaw.SetDeadline(time.Now().Add(p.cfg.dialTimeout))
	if err := clientTLS.Handshake(); err != nil {
		m.handshakeErrors.Add(1)
		slog.Debug("client-side TLS handshake failed", "host", host, "err", err.Error())
		return
	}
	_ = clientRaw.SetDeadline(time.Time{})
	defer clientTLS.Close()

	p.serveDecrypted(clientTLS, host)
}

func (p *proxyHandler) serveDecrypted(clientTLS *tls.Conn, host string) {
	proto := clientTLS.ConnectionState().NegotiatedProtocol

	handler := http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		p.proxyOrigin(w, req, host)
	})

	if proto == "h2" {
		(&http2.Server{
			IdleTimeout: p.cfg.idleTimeout,
		}).ServeConn(clientTLS, &http2.ServeConnOpts{
			Handler: handler,
		})
		return
	}
	br := bufio.NewReader(clientTLS)
	for {
		req, err := http.ReadRequest(br)
		if err != nil {
			return
		}
		rec := &h1Writer{conn: clientTLS}
		handler.ServeHTTP(rec, req)
		if rec.closeAfter || req.Close {
			return
		}
	}
}

func (p *proxyHandler) proxyOrigin(w http.ResponseWriter, req *http.Request, host string) {
	outReq := req.Clone(req.Context())
	outReq.URL.Scheme = "https"
	outReq.URL.Host = host
	outReq.RequestURI = ""
	stripHopByHop(outReq.Header)

	resp, err := p.rt.roundTrip(outReq)
	if err != nil {
		m.upstreamErrors.Add(1)
		slog.Warn("upstream roundtrip failed", "host", host, "err", err.Error())
		http.Error(w, "upstream error", http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	if resp.ProtoMajor == 2 {
		m.h2Upstream.Add(1)
	} else {
		m.h1Upstream.Add(1)
	}

	copyHeader(w.Header(), resp.Header)
	stripHopByHop(w.Header())
	w.WriteHeader(resp.StatusCode)
	n, _ := io.Copy(w, resp.Body)
	m.bytesIn.Add(uint64(n))
}

func (p *proxyHandler) handlePlainHTTP(w http.ResponseWriter, r *http.Request) {
	outReq := r.Clone(r.Context())
	outReq.RequestURI = ""
	if outReq.URL.Scheme == "" {
		outReq.URL.Scheme = "http"
	}
	if outReq.URL.Host == "" {
		outReq.URL.Host = r.Host
	}
	stripHopByHop(outReq.Header)

	transport := &http.Transport{
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			return p.rt.dialUpstream(ctx, addr)
		},
		ForceAttemptHTTP2: true,
	}
	resp, err := transport.RoundTrip(outReq)
	if err != nil {
		m.upstreamErrors.Add(1)
		http.Error(w, "upstream error: "+err.Error(), http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	copyHeader(w.Header(), resp.Header)
	stripHopByHop(w.Header())
	w.WriteHeader(resp.StatusCode)
	n, _ := io.Copy(w, resp.Body)
	m.bytesIn.Add(uint64(n))
}

func (p *proxyHandler) tunnelRaw(w http.ResponseWriter, r *http.Request, target string) {
	hijacker, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "hijacking not supported", http.StatusInternalServerError)
		return
	}
	clientConn, clientBuf, err := hijacker.Hijack()
	if err != nil {
		return
	}
	defer clientConn.Close()

	upstream, err := p.rt.dialUpstream(r.Context(), target)
	if err != nil {
		_ = writeConnectResponse(clientBuf, http.StatusBadGateway, err.Error())
		return
	}
	defer upstream.Close()
	_ = writeConnectResponse(clientBuf, http.StatusOK, "OK")
	pipeRaw(clientConn, upstream)
}

func (rt *upstreamRoundTripper) dialUpstream(ctx context.Context, addr string) (net.Conn, error) {
	dialer := &net.Dialer{Timeout: rt.cfg.dialTimeout}

	if rt.cfg.upstreamProxy == "" {
		return dialer.DialContext(ctx, "tcp", addr)
	}

	u, err := url.Parse(rt.cfg.upstreamProxy)
	if err != nil {
		return nil, fmt.Errorf("invalid UPSTREAM_PROXY: %w", err)
	}
	proxyConn, err := dialer.DialContext(ctx, "tcp", u.Host)
	if err != nil {
		return nil, fmt.Errorf("dial upstream proxy: %w", err)
	}

	req := fmt.Sprintf("CONNECT %s HTTP/1.1\r\nHost: %s\r\n", addr, addr)
	if u.User != nil {
		req += "Proxy-Authorization: Basic " + basicAuth(u.User) + "\r\n"
	}
	req += "\r\n"

	if _, err := proxyConn.Write([]byte(req)); err != nil {
		_ = proxyConn.Close()
		return nil, fmt.Errorf("write CONNECT: %w", err)
	}

	br := bufio.NewReader(proxyConn)
	resp, err := http.ReadResponse(br, &http.Request{Method: "CONNECT"})
	if err != nil {
		_ = proxyConn.Close()
		return nil, fmt.Errorf("read CONNECT response: %w", err)
	}
	_ = resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		_ = proxyConn.Close()
		return nil, fmt.Errorf("upstream proxy returned %s", resp.Status)
	}
	return proxyConn, nil
}
