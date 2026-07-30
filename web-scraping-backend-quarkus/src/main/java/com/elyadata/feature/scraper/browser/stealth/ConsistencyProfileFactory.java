package com.elyadata.feature.scraper.browser.stealth;

import com.elyadata.feature.proxy.domain.model.Proxy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;


@Slf4j
@ApplicationScoped
public class ConsistencyProfileFactory {

    private static final String CHROME_VERSION = "120.0.0.0";
    private static final String LANG_EN_US = "en-US";
    private static final String LANG_EN_GB = "en-GB";
    private static final String LANG_EN = "en";
    private static final String LANG_FR_FR = "fr-FR";
    private static final String LANG_FR = "fr";
    private static final String LANG_DE_DE = "de-DE";
    private static final String LANG_DE = "de";
    private static final String LANG_ES_ES = "es-ES";
    private static final String LANG_ES = "es";
    private static final String LANG_IT_IT = "it-IT";
    private static final String LANG_IT = "it";
    private static final String LANG_NL_NL = "nl-NL";
    private static final String LANG_NL = "nl";
    private static final String LANG_EN_CA = "en-CA";
    private static final String LANG_FR_CA = "fr-CA";
    private static final String LANG_AR_TN = "ar-TN";
    private static final String LANG_JA_JP = "ja-JP";
    private static final String LANG_JA = "ja";
    private static final String LANG_PT_BR = "pt-BR";
    private static final String LANG_PT = "pt";

    private static final String TZ_NEW_YORK = "America/New_York";
    private static final String TZ_LONDON = "Europe/London";
    private static final String TZ_PARIS = "Europe/Paris";
    private static final String TZ_BERLIN = "Europe/Berlin";
    private static final String TZ_MADRID = "Europe/Madrid";
    private static final String TZ_ROME = "Europe/Rome";
    private static final String TZ_AMSTERDAM = "Europe/Amsterdam";
    private static final String TZ_TORONTO = "America/Toronto";
    private static final String TZ_TUNIS = "Africa/Tunis";
    private static final String TZ_TOKYO = "Asia/Tokyo";
    private static final String TZ_SAO_PAULO = "America/Sao_Paulo";

    private record OsBucket(String uaOs, String platform, List<String> gpus, int touchPoints) {
    }

    private static final List<OsBucket> OS_BUCKETS = List.of(
            new OsBucket(
                    "Windows NT 10.0; Win64; x64",
                    "Win32",
                    List.of(
                            "ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                            "ANGLE (NVIDIA, NVIDIA GeForce GTX 1660 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                            "ANGLE (Intel, Intel(R) UHD Graphics 630 Direct3D11 vs_5_0 ps_5_0, D3D11)",
                            "ANGLE (AMD, AMD Radeon RX 6700 XT Direct3D11 vs_5_0 ps_5_0, D3D11)"
                    ),
                    0
            ),
            new OsBucket(
                    "Macintosh; Intel Mac OS X 10_15_7",
                    "MacIntel",
                    List.of(
                            "ANGLE (Apple, Apple M1 Pro, OpenGL 4.1)",
                            "ANGLE (Apple, Apple M2, OpenGL 4.1)",
                            "Intel Iris OpenGL Engine"
                    ),
                    0
            ),
            new OsBucket(
                    "X11; Linux x86_64",
                    "Linux x86_64",
                    List.of(
                            "Mesa Intel(R) UHD Graphics 620 (KBL GT2)",
                            "Mesa DRI Intel(R) HD Graphics 630 (KBL GT2)"
                    ),
                    0
            )
    );

    private record GeoProfile(String timezone, String locale, String language, List<String> languages) {
    }

    private static final Map<String, GeoProfile> COUNTRY_GEO = Map.ofEntries(
            Map.entry("US", new GeoProfile(TZ_NEW_YORK, LANG_EN_US, LANG_EN_US, List.of(LANG_EN_US, LANG_EN))),
            Map.entry("GB", new GeoProfile(TZ_LONDON, LANG_EN_GB, LANG_EN_GB, List.of(LANG_EN_GB, LANG_EN))),
            Map.entry("FR", new GeoProfile(TZ_PARIS, LANG_FR_FR, LANG_FR_FR, List.of(LANG_FR_FR, LANG_FR, LANG_EN_US, LANG_EN))),
            Map.entry("DE", new GeoProfile(TZ_BERLIN, LANG_DE_DE, LANG_DE_DE, List.of(LANG_DE_DE, LANG_DE, LANG_EN))),
            Map.entry("ES", new GeoProfile(TZ_MADRID, LANG_ES_ES, LANG_ES_ES, List.of(LANG_ES_ES, LANG_ES, LANG_EN))),
            Map.entry("IT", new GeoProfile(TZ_ROME, LANG_IT_IT, LANG_IT_IT, List.of(LANG_IT_IT, LANG_IT, LANG_EN))),
            Map.entry("NL", new GeoProfile(TZ_AMSTERDAM, LANG_NL_NL, LANG_NL_NL, List.of(LANG_NL_NL, LANG_NL, LANG_EN))),
            Map.entry("CA", new GeoProfile(TZ_TORONTO, LANG_EN_CA, LANG_EN_CA, List.of(LANG_EN_CA, LANG_EN, LANG_FR_CA))),
            Map.entry("TN", new GeoProfile(TZ_TUNIS, LANG_FR_FR, LANG_FR_FR, List.of(LANG_FR_FR, LANG_FR, LANG_AR_TN, LANG_EN))),
            Map.entry("JP", new GeoProfile(TZ_TOKYO, LANG_JA_JP, LANG_JA_JP, List.of(LANG_JA_JP, LANG_JA, LANG_EN))),
            Map.entry("BR", new GeoProfile(TZ_SAO_PAULO, LANG_PT_BR, LANG_PT_BR, List.of(LANG_PT_BR, LANG_PT, LANG_EN)))
    );

    private static final GeoProfile DEFAULT_GEO = COUNTRY_GEO.get("FR");

    private record ScreenSize(int width, int height, int availWidth, int availHeight) {
    }

    private static final List<ScreenSize> SCREEN_SIZES = List.of(
            new ScreenSize(1920, 1080, 1920, 1040),
            new ScreenSize(1366, 768, 1366, 728),
            new ScreenSize(1536, 864, 1536, 824),
            new ScreenSize(1440, 900, 1440, 860),
            new ScreenSize(2560, 1440, 2560, 1400)
    );

    public ConsistencyProfile buildFor(Proxy proxy) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        OsBucket os = OS_BUCKETS.get(rnd.nextInt(OS_BUCKETS.size()));
        ScreenSize screen = SCREEN_SIZES.get(rnd.nextInt(SCREEN_SIZES.size()));
        GeoProfile geo = resolveGeo(proxy);
        String gpu = os.gpus().get(rnd.nextInt(os.gpus().size()));
        int cores = pickFrom(rnd, 4, 8, 12, 16);
        int memory = pickFrom(rnd, 4, 8, 8, 16);

        String ua = String.format(
                "Mozilla/5.0 (%s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%s Safari/537.36",
                os.uaOs(), CHROME_VERSION);
        int viewportW = Math.min(1366, screen.width() - rnd.nextInt(0, 40));
        int viewportH = Math.min(768, screen.availHeight() - rnd.nextInt(80, 140));

        ConsistencyProfile profile = ConsistencyProfile.builder()
                .userAgent(ua)
                .platform(os.platform())
                .acceptLanguage(buildAcceptLanguage(geo.languages()))
                .language(geo.language())
                .languages(geo.languages())
                .timezone(geo.timezone())
                .locale(geo.locale())
                .webglVendor("Google Inc. (NVIDIA)")
                .webglRenderer(gpu)
                .hardwareConcurrency(cores)
                .deviceMemory(memory)
                .maxTouchPoints(os.touchPoints())
                .vendor("Google Inc.")
                .screenWidth(screen.width())
                .screenHeight(screen.height())
                .screenAvailWidth(screen.availWidth())
                .screenAvailHeight(screen.availHeight())
                .viewportWidth(viewportW)
                .viewportHeight(viewportH)
                .build();

        log.debug("[STEALTH] Built consistency profile: os={} geo={} screen={}x{} gpu={}",
                os.platform(), geo.locale(), screen.width(), screen.height(),
                gpu.substring(0, Math.min(40, gpu.length())));

        return profile;
    }

    private GeoProfile resolveGeo(Proxy proxy) {
        if (proxy == null || proxy.country() == null || proxy.country().isBlank()) {
            return DEFAULT_GEO;
        }
        String code = proxy.country().toUpperCase(Locale.ROOT);
        return COUNTRY_GEO.getOrDefault(code, DEFAULT_GEO);
    }

    private static String buildAcceptLanguage(List<String> languages) {
        StringBuilder sb = new StringBuilder(languages.getFirst());
        double q = 1.0;
        for (int i = 1; i < languages.size(); i++) {
            q -= 0.1;
            sb.append(',').append(languages.get(i)).append(";q=").append(String.format(Locale.ROOT, "%.1f", q));
        }
        return sb.toString();
    }

    private static int pickFrom(ThreadLocalRandom rnd, int... options) {
        return options[rnd.nextInt(options.length)];
    }
}