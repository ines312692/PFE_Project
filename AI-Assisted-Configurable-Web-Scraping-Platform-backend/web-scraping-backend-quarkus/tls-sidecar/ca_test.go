package main

import (
	"crypto/x509"
	"net"
	"testing"
	"time"
)

func TestCAGenerateAndLoad(t *testing.T) {
	dir := t.TempDir()

	cm, err := newCAManager(dir)
	if err != nil {
		t.Fatalf("generate CA: %v", err)
	}
	if !cm.caCert.IsCA {
		t.Fatal("generated cert is not a CA")
	}
	if cm.caCert.MaxPathLen != 0 || !cm.caCert.MaxPathLenZero {
		t.Errorf("expected pathlen:0, got MaxPathLen=%d zero=%v",
			cm.caCert.MaxPathLen, cm.caCert.MaxPathLenZero)
	}

	// Reloading from disk must yield the same CA subject.
	cm2, err := newCAManager(dir)
	if err != nil {
		t.Fatalf("reload CA: %v", err)
	}
	if cm2.caCert.Subject.CommonName != cm.caCert.Subject.CommonName {
		t.Error("reloaded CA subject mismatch")
	}
}

func TestMintLeafChainsToCA(t *testing.T) {
	cm, err := newCAManager(t.TempDir())
	if err != nil {
		t.Fatalf("CA: %v", err)
	}

	leaf, err := cm.mintLeaf("example.com")
	if err != nil {
		t.Fatalf("mint leaf: %v", err)
	}
	if leaf.cert.Leaf == nil {
		// X509KeyPair does not populate Leaf; parse for verification.
		parsed, perr := x509.ParseCertificate(leaf.cert.Certificate[0])
		if perr != nil {
			t.Fatalf("parse leaf: %v", perr)
		}
		leaf.cert.Leaf = parsed
	}

	roots := x509.NewCertPool()
	roots.AddCert(cm.caCert)
	if _, err := leaf.cert.Leaf.Verify(x509.VerifyOptions{
		Roots:       roots,
		DNSName:     "example.com",
		CurrentTime: time.Now(),
	}); err != nil {
		t.Errorf("leaf does not chain to CA: %v", err)
	}
}

func TestMintLeafIPSAN(t *testing.T) {
	cm, err := newCAManager(t.TempDir())
	if err != nil {
		t.Fatalf("CA: %v", err)
	}

	leaf, err := cm.mintLeaf("203.0.113.7")
	if err != nil {
		t.Fatalf("mint leaf for IP: %v", err)
	}
	parsed, err := x509.ParseCertificate(leaf.cert.Certificate[0])
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if len(parsed.IPAddresses) != 1 || !parsed.IPAddresses[0].Equal(net.ParseIP("203.0.113.7")) {
		t.Errorf("IP SAN not set correctly: %v", parsed.IPAddresses)
	}
	if len(parsed.DNSNames) != 0 {
		t.Errorf("IP host should not populate DNSNames: %v", parsed.DNSNames)
	}
}

func TestLeafCacheReuse(t *testing.T) {
	cm, err := newCAManager(t.TempDir())
	if err != nil {
		t.Fatalf("CA: %v", err)
	}
	a, err := cm.leafFor("cache.test")
	if err != nil {
		t.Fatalf("leafFor: %v", err)
	}
	b, err := cm.leafFor("cache.test")
	if err != nil {
		t.Fatalf("leafFor: %v", err)
	}
	if a != b {
		t.Error("expected cached leaf to be reused")
	}
}
