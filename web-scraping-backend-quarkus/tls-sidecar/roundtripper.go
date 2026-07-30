package main

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"time"

	utls "github.com/refraction-networking/utls"
	"golang.org/x/net/http2"
)

type upstreamRoundTripper struct {
	cfg config
}

func newUpstreamRoundTripper(cfg config) *upstreamRoundTripper {
	return &upstreamRoundTripper{cfg: cfg}
}

func chromeH2Transport() *http2.Transport {
	return &http2.Transport{
		MaxHeaderListSize:          262144,
		MaxReadFrameSize:           16384,
		StrictMaxConcurrentStreams: true,
		DisableCompression:         true,
		AllowHTTP:                  false,
		ReadIdleTimeout:            30 * time.Second,
		PingTimeout:                15 * time.Second,
	}
}
func (rt *upstreamRoundTripper) dialChromeTLS(ctx context.Context, host string) (net.Conn, string, error) {
	raw, err := rt.dialUpstream(ctx, host+":443")
	if err != nil {
		return nil, "", fmt.Errorf("tcp dial: %w", err)
	}

	uConn := utls.UClient(raw, &utls.Config{
		ServerName: host,
		NextProtos: []string{"h2", "http/1.1"},
	}, rt.cfg.helloID)

	_ = raw.SetDeadline(time.Now().Add(rt.cfg.dialTimeout))
	if err := uConn.Handshake(); err != nil {
		_ = raw.Close()
		return nil, "", fmt.Errorf("uTLS handshake: %w", err)
	}
	_ = raw.SetDeadline(time.Time{})

	alpn := uConn.ConnectionState().NegotiatedProtocol
	return uConn, alpn, nil
}

func (rt *upstreamRoundTripper) roundTrip(req *http.Request) (*http.Response, error) {
	host := req.URL.Hostname()

	conn, alpn, err := rt.dialChromeTLS(req.Context(), host)
	if err != nil {
		return nil, err
	}

	switch alpn {
	case "h2":
		tr := chromeH2Transport()
		h2Conn, err := tr.NewClientConn(conn)
		if err != nil {
			_ = conn.Close()
			return nil, fmt.Errorf("h2 client conn: %w", err)
		}
		m.httpsHandshakes.Add(1)
		return h2Conn.RoundTrip(req)

	default:
		req.Proto, req.ProtoMajor, req.ProtoMinor = "HTTP/1.1", 1, 1
		req.Close = true
		m.httpsHandshakes.Add(1)
		return rt.http11RoundTrip(conn, req)
	}
}

func (rt *upstreamRoundTripper) http11RoundTrip(conn net.Conn, req *http.Request) (*http.Response, error) {
	if err := req.Write(conn); err != nil {
		_ = conn.Close()
		return nil, fmt.Errorf("write h1 request: %w", err)
	}
	resp, err := http.ReadResponse(newBufReader(conn), req)
	if err != nil {
		_ = conn.Close()
		return nil, fmt.Errorf("read h1 response: %w", err)
	}
	resp.Body = &connClosingBody{ReadCloser: resp.Body, conn: conn}
	return resp, nil
}
