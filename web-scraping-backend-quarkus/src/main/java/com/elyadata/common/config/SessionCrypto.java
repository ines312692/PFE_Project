package com.elyadata.common.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;


@Slf4j
@ApplicationScoped
public class SessionCrypto {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;
    private static final String KEY_PROPERTY = "session.encryption.key";

    private final SecureRandom random = new SecureRandom();
    private final AtomicReference<SecretKeySpec> cachedKey = new AtomicReference<>();

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Session encryption failed: " + e.getMessage(), e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) return null;
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            byte[] ct = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, IV_LENGTH, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Session decryption failed: " + e.getMessage(), e);
        }
    }

    private SecretKeySpec key() {
        SecretKeySpec k = cachedKey.get();
        if (k != null) return k;

        SecretKeySpec loaded = loadKey();
        if (cachedKey.compareAndSet(null, loaded)) {
            log.info("[SESSION-CRYPTO] AES-256-GCM key loaded");
            return loaded;
        }
        return cachedKey.get();
    }

    private SecretKeySpec loadKey() {
        String b64 = ConfigProvider.getConfig()
                .getOptionalValue(KEY_PROPERTY, String.class)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Missing " + KEY_PROPERTY + " (env SESSION_ENCRYPTION_KEY): "
                                + "Base64-encoded 32-byte AES key required for session persistence"));
        byte[] raw = Base64.getDecoder().decode(b64);
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "Session key must be 32 bytes (AES-256), got " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }
}