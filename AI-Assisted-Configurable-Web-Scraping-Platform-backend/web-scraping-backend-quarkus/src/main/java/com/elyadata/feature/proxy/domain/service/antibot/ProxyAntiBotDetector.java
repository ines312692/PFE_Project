package com.elyadata.feature.proxy.domain.service.antibot;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.DetectionResult;

public interface ProxyAntiBotDetector {

    AntiBotProvider provider();

    DetectionResult check(Proxy proxy);
}