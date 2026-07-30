package com.elyadata.common.exceptions.handlers;

import com.elyadata.common.exceptions.ElyaScraperException;
import com.elyadata.common.exceptions.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.OffsetDateTime;
import java.util.Set;

import static com.elyadata.common.utils.InternalErrors.BACK_END_EXCEPTION;

@Slf4j
@Provider
public class ElyaScraperExceptionHandler implements ExceptionMapper<ElyaScraperException> {

    @Override
    public Response toResponse(ElyaScraperException exception) {
        MDC.put("errorCode", String.valueOf(BACK_END_EXCEPTION.getInternalCode()));
        MDC.put("path", exception.getUriPath());
        MDC.put("statusCode", String.valueOf(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
        MDC.put("timestamp", OffsetDateTime.now().toString());

        log.error("[EXCEPTION] {}", exception.getMessage(), exception);

        MDC.clear();

        ErrorResponse errorResponse = ErrorResponse.builder()
                .path(exception.getUriPath())
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
                .errorCode(BACK_END_EXCEPTION.getInternalCode())
                .timestamp(OffsetDateTime.now())
                .message(exception.getMessage())
                .subErrors(Set.of())
                .build();

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(errorResponse)
                .build();
    }
}