package com.elyadata.feature.job.domain.service;

import com.elyadata.feature.job.application.dto.SiteProbeResponse;

public interface SiteProbeService {
    SiteProbeResponse probe(String url);
}