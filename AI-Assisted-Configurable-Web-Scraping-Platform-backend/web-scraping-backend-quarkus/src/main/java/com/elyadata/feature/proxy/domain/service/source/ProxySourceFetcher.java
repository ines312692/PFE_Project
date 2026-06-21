package com.elyadata.feature.proxy.domain.service.source;

import com.elyadata.feature.proxy.domain.model.Proxy;
import com.elyadata.feature.proxy.domain.model.ProxySource;

import java.util.List;

public interface ProxySourceFetcher {

    ProxySource source();

    List<Proxy> fetch();
}
