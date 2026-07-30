package com.elyadata.feature.job.domain.service.webhook;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookSignatureVerifier")
class WebhookSignatureVerifierTest {

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier();

    private static final String SECRET = "super-secret-key";
    private static final byte[] BODY = "{\"event\":\"job.done\"}".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final String TIMESTAMP = String.valueOf(NOW.getEpochSecond());

    private static String sign(String secret, String timestamp, byte[] body) {
        try {
            byte[] ts = timestamp.getBytes(StandardCharsets.UTF_8);
            byte[] dot = {'.'};
            byte[] safe = body == null ? new byte[0] : body;
            byte[] payload = new byte[ts.length + 1 + safe.length];
            System.arraycopy(ts, 0, payload, 0, ts.length);
            System.arraycopy(dot, 0, payload, ts.length, 1);
            System.arraycopy(safe, 0, payload, ts.length + 1, safe.length);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Nested
    @DisplayName("VALID result")
    class ValidResult {

        @Test
        @DisplayName("returns VALID for correct signature within tolerance")
        void validSignature() {
            String sig = sign(SECRET, TIMESTAMP, BODY);
            assertThat(verifier.verify(SECRET, BODY, TIMESTAMP, sig, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.VALID);
        }

        @Test
        @DisplayName("returns VALID with prefix stripped from signature")
        void validWithSha256Prefix() {
            String sig = sign(SECRET, TIMESTAMP, BODY); // already has sha256= prefix
            assertThat(verifier.verify(SECRET, BODY, TIMESTAMP, sig, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.VALID);
        }

        @Test
        @DisplayName("returns VALID for empty body")
        void validEmptyBody() {
            String sig = sign(SECRET, TIMESTAMP, new byte[0]);
            assertThat(verifier.verify(SECRET, new byte[0], TIMESTAMP, sig, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.VALID);
        }

        @Test
        @DisplayName("returns VALID for null body treated as empty")
        void validNullBody() {
            String sig = sign(SECRET, TIMESTAMP, new byte[0]);
            assertThat(verifier.verify(SECRET, null, TIMESTAMP, sig, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.VALID);
        }

        @Test
        @DisplayName("returns VALID just within 5-minute tolerance (4m59s offset)")
        void validAtEdgeOfWindow() {
            Instant slightly_past = NOW.plusSeconds(299);
            String sig = sign(SECRET, TIMESTAMP, BODY);
            assertThat(verifier.verify(SECRET, BODY, TIMESTAMP, sig, slightly_past))
                    .isEqualTo(WebhookSignatureVerifier.Result.VALID);
        }
    }


    @Nested
    @DisplayName("MISSING_HEADERS result")
    class MissingHeaders {

        @Test
        @DisplayName("null secret → MISSING_HEADERS")
        void nullSecret() {
            String sig = sign(SECRET, TIMESTAMP, BODY);
            assertThat(verifier.verify(null, BODY, TIMESTAMP, sig, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.MISSING_HEADERS);
        }

        @Test
        @DisplayName("blank secret → MISSING_HEADERS")
        void blankSecret() {
            String sig = sign(SECRET, TIMESTAMP, BODY);
            assertThat(verifier.verify("   ", BODY, TIMESTAMP, sig, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.MISSING_HEADERS);
        }

        @Test
        @DisplayName("null timestamp → MISSING_HEADERS")
        void nullTimestamp() {
            String sig = sign(SECRET, TIMESTAMP, BODY);
            assertThat(verifier.verify(SECRET, BODY, null, sig, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.MISSING_HEADERS);
        }

        @Test
        @DisplayName("null signature → MISSING_HEADERS")
        void nullSignature() {
            assertThat(verifier.verify(SECRET, BODY, TIMESTAMP, null, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.MISSING_HEADERS);
        }

        @Test
        @DisplayName("non-numeric timestamp → MISSING_HEADERS")
        void nonNumericTimestamp() {
            String sig = sign(SECRET, TIMESTAMP, BODY);
            assertThat(verifier.verify(SECRET, BODY, "not-a-number", sig, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.MISSING_HEADERS);
        }
    }

    // ── STALE_TIMESTAMP ───────────────────────────────────────────────────

    @Nested
    @DisplayName("STALE_TIMESTAMP result")
    class StaleTimestamp {

        @Test
        @DisplayName("request older than 5 minutes → STALE_TIMESTAMP")
        void tooOld() {
            Instant sixMinutesLater = NOW.plusSeconds(361);
            String sig = sign(SECRET, TIMESTAMP, BODY);
            assertThat(verifier.verify(SECRET, BODY, TIMESTAMP, sig, sixMinutesLater))
                    .isEqualTo(WebhookSignatureVerifier.Result.STALE_TIMESTAMP);
        }

        @Test
        @DisplayName("request in the future beyond tolerance → STALE_TIMESTAMP")
        void futureTimestamp() {
            long futureTs = NOW.getEpochSecond() + 361;
            String futureTimestamp = String.valueOf(futureTs);
            String sig = sign(SECRET, futureTimestamp, BODY);
            assertThat(verifier.verify(SECRET, BODY, futureTimestamp, sig, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.STALE_TIMESTAMP);
        }
    }


    @Nested
    @DisplayName("BAD_SIGNATURE result")
    class BadSignature {

        @Test
        @DisplayName("wrong secret → BAD_SIGNATURE")
        void wrongSecret() {
            String sig = sign("wrong-secret", TIMESTAMP, BODY);
            assertThat(verifier.verify(SECRET, BODY, TIMESTAMP, sig, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.BAD_SIGNATURE);
        }

        @Test
        @DisplayName("tampered body → BAD_SIGNATURE")
        void tamperedBody() {
            String sig = sign(SECRET, TIMESTAMP, BODY);
            byte[] tampered = "{\"event\":\"hacked\"}".getBytes(StandardCharsets.UTF_8);
            assertThat(verifier.verify(SECRET, tampered, TIMESTAMP, sig, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.BAD_SIGNATURE);
        }

        @Test
        @DisplayName("signature without prefix is still checked correctly")
        void signatureWithoutPrefix() {
            String sigWithPrefix = sign(SECRET, TIMESTAMP, BODY);
            String rawHex = sigWithPrefix.substring("sha256=".length());
            assertThat(verifier.verify(SECRET, BODY, TIMESTAMP, rawHex, NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.VALID);
        }

        @Test
        @DisplayName("all-zeros signature → BAD_SIGNATURE")
        void zerosSignature() {
            assertThat(verifier.verify(SECRET, BODY, TIMESTAMP, "sha256=0000000000000000", NOW))
                    .isEqualTo(WebhookSignatureVerifier.Result.BAD_SIGNATURE);
        }
    }
}
