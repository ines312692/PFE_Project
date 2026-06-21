package main

import (
	"bufio"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// hopByHopHeaders must not be forwarded by a proxy (RFC 7230 §6.1).
var hopByHopHeaders = []string{
	"Connection",
	"Proxy-Connection",
	"Keep-Alive",
	"Proxy-Authenticate",
	"Proxy-Authorization",
	"Te",
	"Trailer",
	"Transfer-Encoding",
	"Upgrade",
}

func stripHopByHop(h http.Header) {
	// Honor any connection-specific headers the peer listed, then drop the
	// standard hop-by-hop set.
	for _, conn := range h.Values("Connection") {
		for _, name := range strings.Split(conn, ",") {
			if name = strings.TrimSpace(name); name != "" {
				h.Del(name)
			}
		}
	}
	for _, name := range hopByHopHeaders {
		h.Del(name)
	}
}

func copyHeader(dst, src http.Header) {
	for k, vv := range src {
		for _, v := range vv {
			dst.Add(k, v)
		}
	}
}

// h1Writer is a minimal http.ResponseWriter that serializes a single HTTP/1.1
// response directly onto the decrypted client connection. It exists because we
// drive HTTP/1.1 client connections manually (the std server can't sit on a
// hijacked conn that we also MITM).
type h1Writer struct {
	conn        net.Conn
	header      http.Header
	wroteHeader bool
	closeAfter  bool
}

func (w *h1Writer) Header() http.Header {
	if w.header == nil {
		w.header = make(http.Header)
	}
	return w.header
}

func (w *h1Writer) WriteHeader(status int) {
	if w.wroteHeader {
		return
	}
	w.wroteHeader = true

	// We don't attempt chunked re-framing; close the connection after the body
	// so length delimitation is unambiguous. Scraping clients reconnect freely.
	w.Header().Del("Transfer-Encoding")
	w.Header().Set("Connection", "close")
	w.closeAfter = true

	var b strings.Builder
	fmt.Fprintf(&b, "HTTP/1.1 %d %s\r\n", status, http.StatusText(status))
	_ = w.header.Write(&b)
	b.WriteString("\r\n")
	_, _ = io.WriteString(w.conn, b.String())
}

func (w *h1Writer) Write(p []byte) (int, error) {
	if !w.wroteHeader {
		w.WriteHeader(http.StatusOK)
	}
	n, err := w.conn.Write(p)
	m.bytesOut.Add(uint64(n))
	return n, err
}

// connClosingBody ties an HTTP/1.1 response body to its connection: closing the
// body closes the connection, since we use Connection: close upstream.
type connClosingBody struct {
	io.ReadCloser
	conn net.Conn
	once sync.Once
}

func (b *connClosingBody) Close() error {
	err := b.ReadCloser.Close()
	b.once.Do(func() { _ = b.conn.Close() })
	return err
}

func newBufReader(c net.Conn) *bufio.Reader {
	return bufio.NewReader(c)
}

func pipeRaw(a, b net.Conn) {
	var wg sync.WaitGroup
	wg.Add(2)

	copyAndCount := func(dst, src net.Conn, counter *atomic.Uint64) {
		defer wg.Done()
		n, _ := io.Copy(dst, src)
		counter.Add(uint64(n))
		if cw, ok := dst.(interface{ CloseWrite() error }); ok {
			_ = cw.CloseWrite()
		}
	}

	go copyAndCount(a, b, &m.bytesIn)
	go copyAndCount(b, a, &m.bytesOut)
	wg.Wait()
}

func writeConnectResponse(buf *bufio.ReadWriter, status int, msg string) error {
	statusText := http.StatusText(status)
	if statusText == "" {
		statusText = msg
	}
	_, err := fmt.Fprintf(buf, "HTTP/1.1 %d %s\r\n\r\n", status, statusText)
	if err != nil {
		return err
	}
	return buf.Flush()
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func basicAuth(u *url.Userinfo) string {
	pw, _ := u.Password()
	creds := u.Username() + ":" + pw
	return base64.StdEncoding.EncodeToString([]byte(creds))
}

func getenv(k, def string) string {
	v := os.Getenv(k)
	if v == "" {
		return def
	}
	return v
}

func parseDuration(envKey string, def time.Duration) time.Duration {
	v := os.Getenv(envKey)
	if v == "" {
		return def
	}
	d, err := time.ParseDuration(v)
	if err != nil {
		return def
	}
	return d
}

func maskProxy(s string) string {
	if s == "" {
		return "<none>"
	}
	u, err := url.Parse(s)
	if err != nil {
		return "<invalid>"
	}
	if u.User != nil {
		u.User = url.User(u.User.Username() + ":***")
	}
	return u.String()
}
