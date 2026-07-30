package com.elyadata.common.exceptions.handlers.domain;

import com.elyadata.common.exceptions.ErrorResponse;
import com.elyadata.common.exceptions.domain.DomainException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.OffsetDateTime;

@Slf4j
@Provider
public class DomainExceptionHandler implements ExceptionMapper<DomainException> {

    @Override
    public Response toResponse(DomainException ex) {
        OffsetDateTime now = OffsetDateTime.now();

        MDC.put("errorCode", String.valueOf(ex.getType().getInternalError().getInternalCode()));
        MDC.put("errorType", ex.getType().name());
        MDC.put("path", String.valueOf(ex.getUriPath()));
        MDC.put("statusCode", String.valueOf(ex.getType().getHttpStatus().getStatusCode()));
        MDC.put("timestamp", now.toString());

        log.warn("[DOMAIN][{}] path={} | {}", ex.getType().name(), ex.getUriPath(), ex.getMessage());

        MDC.clear();

        ErrorResponse errorResponse = ErrorResponse.builder()
                .path(ex.getUriPath())
                .message(ex.getMessage())
                .subErrors(ex.getSubErrors())
                .statusCode(ex.getType().getHttpStatus().getStatusCode())
                .errorCode(ex.getType().getInternalError().getInternalCode())
                .timestamp(now)
                .build();

        return Response.status(ex.getType().getHttpStatus())
                .entity(errorResponse)
                .build();
    }
}