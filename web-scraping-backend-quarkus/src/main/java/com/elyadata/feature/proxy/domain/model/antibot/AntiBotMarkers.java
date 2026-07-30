package com.elyadata.feature.proxy.domain.model.antibot;

import java.util.List;

public final class AntiBotMarkers {

    private AntiBotMarkers() {
    }

    public static final class Cloudflare {
        public static final List<String> HEADERS = List.of(
                "cf-ray",
                "cf-cache-status",
                "cf-request-id",
                "cf-bgj"
        );

        public static final List<String> SERVER_VALUES = List.of(
                "cloudflare"
        );

        public static final List<String> BODY_DETECT_MARKERS = List.of(
                "cloudflare"
        );

        public static final List<String> BODY_BLOCK_MARKERS = List.of(
                "just a moment...",
                "cf-challenge",
                "__cf_chl_",
                "attention required",
                "sorry, you have been blocked",
                "error 1020",
                "cf-error-details"
        );

        public static final String NS_SUFFIX = ".ns.cloudflare.com";

        private Cloudflare() {
        }
    }

    public static final class DataDome {
        public static final List<String> HEADERS = List.of(
                "x-datadome",
                "x-datadome-cid",
                "x-dd-b"
        );

        public static final List<String> BODY_DETECT_MARKERS = List.of(
                "datadome",
                "dd_cookie_test",
                "geo.captcha-delivery.com",
                "/captcha-delivery"
        );

        public static final List<String> BODY_BLOCK_MARKERS = List.of(
                "please complete the security check",
                "/captcha-delivery",
                "datadome-captcha",
                "blocked by datadome"
        );

        private DataDome() {
        }
    }

    public static final class Akamai {
        public static final List<String> HEADERS = List.of(
                "x-akamai-request-id",
                "x-akamai-transformed",
                "x-check-cacheable",
                "akamai-grn",
                "x-akamai-session-info"
        );

        public static final List<String> SERVER_VALUES = List.of(
                "akamaighost",
                "akamainetstorage"
        );

        public static final List<String> BODY_DETECT_MARKERS = List.of(
                "akamai"
        );

        public static final List<String> BODY_BLOCK_MARKERS = List.of(
                "access denied",
                "reference #",
                "akamai_bot_manager",
                "_abck"
        );

        private Akamai() {
        }
    }

    public static final class PerimeterX {
        public static final List<String> HEADERS = List.of(
                "x-px-block",
                "x-px-action",
                "x-px-device-fp"
        );

        public static final List<String> COOKIE_MARKERS = List.of(
                "_px",
                "_pxhd",
                "_pxvid",
                "_px2",
                "_px3"
        );

        public static final List<String> BODY_DETECT_MARKERS = List.of(
                "perimeterx",
                "px-captcha",
                "/_px/",
                "client.perimeterx.net"
        );

        public static final List<String> BODY_BLOCK_MARKERS = List.of(
                "px-captcha",
                "please verify you are a human",
                "access to this page has been denied"
        );

        private PerimeterX() {
        }
    }

    public static final class Captcha {
        public static final List<String> HCAPTCHA_MARKERS = List.of(
                "hcaptcha.com",
                "h-captcha",
                "data-hcaptcha-sitekey",
                "js.hcaptcha.com"
        );

        public static final List<String> RECAPTCHA_MARKERS = List.of(
                "google.com/recaptcha",
                "gstatic.com/recaptcha",
                "g-recaptcha",
                "data-sitekey",
                "grecaptcha"
        );

        public static final List<String> TURNSTILE_MARKERS = List.of(
                "challenges.cloudflare.com/turnstile",
                "cf-turnstile",
                "data-cf-turnstile-sitekey"
        );

        private Captcha() {
        }
    }
}