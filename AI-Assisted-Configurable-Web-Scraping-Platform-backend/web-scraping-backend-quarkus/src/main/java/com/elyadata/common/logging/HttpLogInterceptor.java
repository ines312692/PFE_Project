package com.elyadata.common.logging;

import com.elyadata.common.utils.LogsConstant;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Provider
@Slf4j
public class HttpLogInterceptor implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String SESSION_ID_HEADER = "X-Session-Id";
    public static final String ORIGIN_ID_HEADER = "X-Origin-Id";

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String SESSION_ID_KEY = "sessionId";
    public static final String ORIGIN_ID_KEY = "originId";
    public static final String HTTP_METHOD_KEY = "httpMethod";
    public static final String ENDPOINT_KEY = "endPoint";
    public static final String HTTP_STATUS_KEY = "httpStatus";
    public static final String DURATION_MICROS_KEY = "durationMicros";
    public static final String DURATION_MS_KEY = "durationMs";
    public static final String HAS_BODY_KEY = "hasBody";
    public static final String RESPONSE_BODY_KEY = "responseBody";

    private static final String START_TIME_PROP = "startTime";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        requestContext.setProperty(START_TIME_PROP, System.nanoTime());

        String correlationId = LoggingMethods.extractHeaderValue(
                CORRELATION_ID_HEADER, requestContext, UUID.randomUUID().toString()
        );
        String sessionId = requestContext.getHeaders().getFirst(SESSION_ID_HEADER);
        String originId = requestContext.getHeaders().getFirst(ORIGIN_ID_HEADER);
        String method = requestContext.getRequest().getMethod();
        String endpoint = requestContext.getUriInfo().getPath();

        Map<String, String> mdcData = new HashMap<>();
        mdcData.put(CORRELATION_ID_KEY, correlationId);
        mdcData.put(SESSION_ID_KEY, sessionId != null ? sessionId : "");
        mdcData.put(ORIGIN_ID_KEY, originId != null ? originId : "");
        mdcData.put(HTTP_METHOD_KEY, method);
        mdcData.put(ENDPOINT_KEY, endpoint);
        mdcData.put(LogsConstant.Keys.REQUEST_ID, correlationId);
        LoggingMethods.insertIntoMDC(mdcData);

        log.info("Incoming HTTP request");

        if (log.isDebugEnabled()) {
            LoggingMethods.enrichMDC(HAS_BODY_KEY, String.valueOf(requestContext.hasEntity()));
            log.debug("Request body presence checked");
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {

        long processingTimeMicros = resolveProcessingTime(requestContext);
        int statusCode = responseContext.getStatus();

        LoggingMethods.enrichMDC(HTTP_STATUS_KEY, String.valueOf(statusCode));
        LoggingMethods.enrichMDC(DURATION_MICROS_KEY, String.valueOf(processingTimeMicros));
        LoggingMethods.enrichMDC(DURATION_MS_KEY, String.valueOf(processingTimeMicros / 1000));

        log.info("Outgoing HTTP response");

        if (log.isDebugEnabled()) {
            String body = responseContext.getEntity() != null
                    ? responseContext.getEntity().toString()
                    : "null";
            LoggingMethods.enrichMDC(RESPONSE_BODY_KEY, body);
            log.debug("Response body captured");
        }

        if (statusCode >= 500) {
            log.error("HTTP server error response");
        } else if (statusCode >= 400) {
            log.warn("HTTP client error response");
        }

        MDC.clear();
    }

    private long resolveProcessingTime(ContainerRequestContext requestContext) {
        Object startTime = requestContext.getProperty(START_TIME_PROP);
        if (startTime instanceof Long start) {
            return TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);
        }
        return -1L;
    }
}