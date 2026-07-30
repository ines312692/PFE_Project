package com.elyadata.common.logging;

import jakarta.ws.rs.container.ContainerRequestContext;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.util.Map;

@UtilityClass
public class LoggingMethods {

    public static String extractHeaderValue(String headerKey,
                                            ContainerRequestContext requestContext,
                                            String defaultValue) {
        String value = requestContext.getHeaders().getFirst(headerKey);
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }

    public static void insertIntoMDC(Map<String, String> data) {
        if (data == null) return;
        data.forEach((key, value) -> {
            if (StringUtils.isNotBlank(key) && value != null) {
                MDC.put(key, value);
            }
        });
    }

    public static void enrichMDC(String key, String value) {
        if (StringUtils.isNotBlank(key) && value != null) {
            MDC.put(key, value);
        }
    }

}