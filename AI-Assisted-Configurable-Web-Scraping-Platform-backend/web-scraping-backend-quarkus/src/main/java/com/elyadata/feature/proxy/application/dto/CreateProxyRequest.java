package com.elyadata.feature.proxy.application.dto;

import com.elyadata.feature.proxy.domain.model.ProxyAnonymity;
import com.elyadata.feature.proxy.domain.model.ProxyProtocol;
import jakarta.validation.constraints.*;

public record CreateProxyRequest(
        @NotBlank(message = "Host is required")
        @Pattern(
                regexp = "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*|(?:\\d{1,3}\\.){3}\\d{1,3})$",
                message = "Invalid host (must be a domain name or IPv4 address)"
        )
        String host,

        @NotNull(message = "Port is required")
        @Min(value = 1, message = "Port must be ≥ 1")
        @Max(value = 65535, message = "Port must be ≤ 65535")
        Integer port,

        @NotNull(message = "Protocol is required")
        ProxyProtocol protocol,

        String username,
        String password,

        @Size(min = 2, max = 2, message = "Country must be a 2-letter ISO code")
        String country,

        ProxyAnonymity anonymity
) {
}