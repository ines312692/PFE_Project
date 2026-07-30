package com.elyadata.feature.job.domain.service.webhook;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;


@Slf4j
@ApplicationScoped
public class WebhookSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final Duration REPLAY_TOLERANCE = Duration.ofMinutes(5);

    public enum Result {
        VALID,
        MISSING_HEADERS,
        STALE_TIMESTAMP,
        BAD_SIGNATURE
    }

    public Result verify(String secret,
                         byte[] body,
                         String timestampHdr,
                         String signatureHdr,
                         Instant now) {

        if (secret == null || secret.isBlank()
                || timestampHdr == null || timestampHdr.isBlank()
                || signatureHdr == null || signatureHdr.isBlank()) {
            return Result.MISSING_HEADERS;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHdr.trim());
        } catch (NumberFormatException e) {
            return Result.MISSING_HEADERS;
        }

        Instant requestTime = Instant.ofEpochSecond(timestamp);
        if (Duration.between(requestTime, now).abs().compareTo(REPLAY_TOLERANCE) > 0) {
            return Result.STALE_TIMESTAMP;
        }

        byte[] signedPayload = buildSignedPayload(timestampHdr.trim(), body);
        String expected = computeHmacHex(secret, signedPayload);
        String received = stripPrefix(signatureHdr.trim());

        return constantTimeEquals(expected, received) ? Result.VALID : Result.BAD_SIGNATURE;
    }

    private static byte[] buildSignedPayload(String timestamp, byte[] body) {
        byte[] tsBytes = timestamp.getBytes(StandardCharsets.UTF_8);
        byte[] dot = {'.'};
        byte[] safeBody = body == null ? new byte[0] : body;

        byte[] out = new byte[tsBytes.length + dot.length + safeBody.length];
        System.arraycopy(tsBytes, 0, out, 0, tsBytes.length);
        System.arraycopy(dot, 0, out, tsBytes.length, dot.length);
        System.arraycopy(safeBody, 0, out, tsBytes.length + dot.length, safeBody.length);
        return out;
    }

    private static String computeHmacHex(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String stripPrefix(String header) {
        return header.startsWith(SIGNATURE_PREFIX)
                ? header.substring(SIGNATURE_PREFIX.length())
                : header;
    }

    private static boolean constantTimeEquals(String expected, String received) {
        if (expected == null || received == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = received.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}