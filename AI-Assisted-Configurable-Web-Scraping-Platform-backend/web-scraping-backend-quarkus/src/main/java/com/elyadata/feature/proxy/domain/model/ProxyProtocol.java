package com.elyadata.feature.proxy.domain.model;

public enum ProxyProtocol {
    HTTP("http"),
    HTTPS("https"),
    SOCKS4("socks4"),
    SOCKS5("socks5");

    private final String scheme;

    ProxyProtocol(String scheme) {
        this.scheme = scheme;
    }

    public String scheme() {
        return scheme;
    }
}