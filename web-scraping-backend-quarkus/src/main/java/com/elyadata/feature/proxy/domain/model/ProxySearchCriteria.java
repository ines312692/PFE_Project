package com.elyadata.feature.proxy.domain.model;

import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.ProtectionStatus;
import lombok.Builder;

@Builder
public record ProxySearchCriteria(
        String search,
        ProxyStatus status,
        ProxyProtocol protocol,
        ProxySource source,
        String country,
        AntiBotProvider antiBotProvider,
        ProtectionStatus antiBotStatus,
        String sortBy,
        String sortOrder
) {
    public static ProxySearchCriteria empty() {
        return ProxySearchCriteria.builder().build();
    }
}