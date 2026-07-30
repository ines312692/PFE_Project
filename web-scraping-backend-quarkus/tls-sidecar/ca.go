package main

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"errors"
	"fmt"
	"log/slog"
	"math/big"
	"net"
	"os"
	"path/filepath"
	"sync"
	"time"
)

type caManager struct {
	caCert    *x509.Certificate
	caKey     *ecdsa.PrivateKey
	caPEMPath string

	mu        sync.RWMutex
	leafCache map[string]*tlsLeaf
}

type tlsLeaf struct {
	cert    tls.Certificate
	expires time.Time
}

func newCAManager(caDir string) (*caManager, error) {
	if err := os.MkdirAll(caDir, 0o700); err != nil {
		return nil, fmt.Errorf("create ca dir: %w", err)
	}
	certPath := filepath.Join(caDir, "ca.crt")
	keyPath := filepath.Join(caDir, "ca.key")

	cm := &caManager{
		leafCache: make(map[string]*tlsLeaf),
		caPEMPath: certPath,
	}

	if fileExists(certPath) && fileExists(keyPath) {
		if err := cm.load(certPath, keyPath); err != nil {
			return nil, fmt.Errorf("load existing CA: %w", err)
		}
		slog.Info("loaded existing CA", "cert", certPath)
		return cm, nil
	}

	if err := cm.generate(certPath, keyPath); err != nil {
		return nil, fmt.Errorf("generate CA: %w", err)
	}
	slog.Info("generated new local CA", "cert", certPath,
		"note", "install this cert in your browser/OS trust store to avoid --ignore-certificate-errors")
	return cm, nil
}

func (cm *caManager) generate(certPath, keyPath string) error {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return err
	}

	serial, err := randSerial()
	if err != nil {
		return err
	}

	template := &x509.Certificate{
		SerialNumber: serial,
		Subject: pkix.Name{
			CommonName:   "Elyadata TLS Sidecar Local CA",
			Organization: []string{"Elyadata"},
		},
		NotBefore:             time.Now().Add(-1 * time.Hour),
		NotAfter:              time.Now().AddDate(10, 0, 0),
		KeyUsage:              x509.KeyUsageCertSign | x509.KeyUsageCRLSign | x509.KeyUsageDigitalSignature,
		BasicConstraintsValid: true,
		IsCA:                  true,
		// This CA only ever signs leaf certificates, never intermediates.
		// pathlen:0 (MaxPathLen=0 + MaxPathLenZero=true) expresses that intent.
		MaxPathLen:     0,
		MaxPathLenZero: true,
	}

	derBytes, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		return err
	}

	caCert, err := x509.ParseCertificate(derBytes)
	if err != nil {
		return err
	}
	cm.caCert = caCert
	cm.caKey = key

	if err := writePEM(certPath, "CERTIFICATE", derBytes, 0o644); err != nil {
		return err
	}
	keyBytes, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		return err
	}
	return writePEM(keyPath, "EC PRIVATE KEY", keyBytes, 0o600)
}

func (cm *caManager) load(certPath, keyPath string) error {
	certPEM, err := os.ReadFile(certPath)
	if err != nil {
		return err
	}
	block, _ := pem.Decode(certPEM)
	if block == nil {
		return errors.New("cert PEM decode failed")
	}
	caCert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		return err
	}

	keyPEM, err := os.ReadFile(keyPath)
	if err != nil {
		return err
	}
	keyBlock, _ := pem.Decode(keyPEM)
	if keyBlock == nil {
		return errors.New("key PEM decode failed")
	}
	caKey, err := x509.ParseECPrivateKey(keyBlock.Bytes)
	if err != nil {
		return err
	}

	cm.caCert = caCert
	cm.caKey = caKey
	return nil
}

func (cm *caManager) leafFor(host string) (*tlsLeaf, error) {
	cm.mu.RLock()
	if leaf, ok := cm.leafCache[host]; ok && time.Now().Before(leaf.expires) {
		cm.mu.RUnlock()
		return leaf, nil
	}
	cm.mu.RUnlock()

	cm.mu.Lock()
	defer cm.mu.Unlock()
	if leaf, ok := cm.leafCache[host]; ok && time.Now().Before(leaf.expires) {
		return leaf, nil
	}

	leaf, err := cm.mintLeaf(host)
	if err != nil {
		return nil, err
	}
	cm.leafCache[host] = leaf
	return leaf, nil
}

func (cm *caManager) mintLeaf(host string) (*tlsLeaf, error) {
	leafKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, err
	}
	serial, err := randSerial()
	if err != nil {
		return nil, err
	}

	notAfter := time.Now().AddDate(1, 0, 0)

	template := &x509.Certificate{
		SerialNumber: serial,
		Subject:      pkix.Name{CommonName: host},
		NotBefore:    time.Now().Add(-1 * time.Hour),
		NotAfter:     notAfter,
		KeyUsage:     x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	if ip := net.ParseIP(host); ip != nil {
		template.IPAddresses = []net.IP{ip}
	} else {
		template.DNSNames = []string{host}
	}

	derBytes, err := x509.CreateCertificate(rand.Reader, template, cm.caCert, &leafKey.PublicKey, cm.caKey)
	if err != nil {
		return nil, err
	}

	certPEM := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: derBytes})
	keyBytes, err := x509.MarshalECPrivateKey(leafKey)
	if err != nil {
		return nil, err
	}
	keyPEM := pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: keyBytes})

	cert, err := tls.X509KeyPair(certPEM, keyPEM)
	if err != nil {
		return nil, fmt.Errorf("build leaf keypair: %w", err)
	}

	return &tlsLeaf{
		cert: cert,
		// Refresh the cache slightly before the cert actually expires.
		expires: notAfter.Add(-24 * time.Hour),
	}, nil
}

func randSerial() (*big.Int, error) {
	return rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
}

func writePEM(path, typ string, der []byte, mode os.FileMode) error {
	f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
	if err != nil {
		return err
	}
	defer f.Close()
	return pem.Encode(f, &pem.Block{Type: typ, Bytes: der})
}

func fileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}
