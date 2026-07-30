package com.elyadata.feature.job.domain.service.port;

import java.util.Optional;


public interface PageRendererPort {

    Optional<String> renderHtml(String url, boolean stealth);
}