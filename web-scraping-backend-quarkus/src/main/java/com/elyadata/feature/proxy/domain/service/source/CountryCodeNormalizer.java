package com.elyadata.feature.proxy.domain.service.source;


import java.util.List;
import java.util.Objects;

public final class CountryCodeNormalizer {

    private CountryCodeNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() == 2 && trimmed.matches("[A-Za-z]{2}")) {
            return trimmed.toUpperCase();
        }
        String iso2 = ISO3_TO_ISO2.get(trimmed.toUpperCase());
        if (iso2 != null) return iso2;
        return NAME_TO_ISO2.get(trimmed.toLowerCase());
    }

    private static final java.util.Map<String, String> ISO3_TO_ISO2 = java.util.Map.ofEntries(
            java.util.Map.entry("USA", "US"), java.util.Map.entry("GBR", "GB"),
            java.util.Map.entry("FRA", "FR"), java.util.Map.entry("DEU", "DE"),
            java.util.Map.entry("ESP", "ES"), java.util.Map.entry("ITA", "IT"),
            java.util.Map.entry("NLD", "NL"), java.util.Map.entry("BEL", "BE"),
            java.util.Map.entry("CAN", "CA"), java.util.Map.entry("MEX", "MX"),
            java.util.Map.entry("BRA", "BR"), java.util.Map.entry("ARG", "AR"),
            java.util.Map.entry("CHN", "CN"), java.util.Map.entry("JPN", "JP"),
            java.util.Map.entry("KOR", "KR"), java.util.Map.entry("IND", "IN"),
            java.util.Map.entry("IDN", "ID"), java.util.Map.entry("VNM", "VN"),
            java.util.Map.entry("THA", "TH"), java.util.Map.entry("PHL", "PH"),
            java.util.Map.entry("RUS", "RU"), java.util.Map.entry("UKR", "UA"),
            java.util.Map.entry("POL", "PL"), java.util.Map.entry("TUR", "TR"),
            java.util.Map.entry("EGY", "EG"), java.util.Map.entry("ZAF", "ZA"),
            java.util.Map.entry("AUS", "AU"), java.util.Map.entry("NZL", "NZ"),
            java.util.Map.entry("HKG", "HK"), java.util.Map.entry("SGP", "SG"),
            java.util.Map.entry("TWN", "TW")
    );

    private static final java.util.Map<String, String> NAME_TO_ISO2 = java.util.Map.ofEntries(
            java.util.Map.entry("united states", "US"),
            java.util.Map.entry("united states of america", "US"),
            java.util.Map.entry("united kingdom", "GB"),
            java.util.Map.entry("great britain", "GB"),
            java.util.Map.entry("france", "FR"),
            java.util.Map.entry("germany", "DE"),
            java.util.Map.entry("spain", "ES"),
            java.util.Map.entry("italy", "IT"),
            java.util.Map.entry("netherlands", "NL"),
            java.util.Map.entry("china", "CN"),
            java.util.Map.entry("japan", "JP"),
            java.util.Map.entry("south korea", "KR"),
            java.util.Map.entry("korea", "KR"),
            java.util.Map.entry("india", "IN"),
            java.util.Map.entry("indonesia", "ID"),
            java.util.Map.entry("vietnam", "VN"),
            java.util.Map.entry("russia", "RU"),
            java.util.Map.entry("russian federation", "RU"),
            java.util.Map.entry("hong kong", "HK"),
            java.util.Map.entry("singapore", "SG"),
            java.util.Map.entry("taiwan", "TW")
    );

    public static List<String> normalizeAll(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        return raw.stream()
                .map(CountryCodeNormalizer::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}