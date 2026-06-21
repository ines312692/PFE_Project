package com.elyadata.feature.audit.infrastructure.interceptor;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class RequestBodyCaptureFilter implements ContainerRequestFilter {

    public static final String CAPTURED_BODY_PROP = "audit.capturedBody";
    private static final int MAX_BYTES = 4 * 1024;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        if (!ctx.hasEntity()) return;

        String method = ctx.getMethod();
        if (!"POST".equalsIgnoreCase(method)
                && !"PUT".equalsIgnoreCase(method)
                && !"PATCH".equalsIgnoreCase(method)) {
            return;
        }

        InputStream original = ctx.getEntityStream();
        byte[] buf = original.readAllBytes();

        byte[] sample = buf.length <= MAX_BYTES
                ? buf
                : new byte[MAX_BYTES];
        if (buf.length > MAX_BYTES) {
            System.arraycopy(buf, 0, sample, 0, MAX_BYTES);
        }
        String captured = new String(sample, StandardCharsets.UTF_8);
        if (buf.length > MAX_BYTES) captured += "...[truncated]";

        ctx.setProperty(CAPTURED_BODY_PROP, captured);
        ctx.setEntityStream(new ByteArrayInputStream(buf));
    }
}