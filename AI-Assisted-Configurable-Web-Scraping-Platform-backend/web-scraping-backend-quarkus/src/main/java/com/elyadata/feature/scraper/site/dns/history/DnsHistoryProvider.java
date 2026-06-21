package com.elyadata.feature.scraper.site.dns.history;

import com.elyadata.feature.scraper.site.model.HistoricalDnsRecord;

import java.util.List;

public interface DnsHistoryProvider {

    String name();

    boolean isEnabled();

    int priority();

    List<HistoricalDnsRecord> fetchHistory(String host);
}