package com.elyadata.common.exceptions.handlers.infrastructure;

import com.elyadata.common.exceptions.ErrorResponse;
import com.elyadata.common.exceptions.infrastructure.Infrastructure5xxException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;

import static com.elyadata.common.utils.InternalErrors.INFRASTRUCTURE_5XX_ERROR;

@Slf4j
@Provider
public class Infrastructure5xxExceptionHandler implements ExceptionMapper<Infrastructure5xxException> {

    @Override
    public Response toResponse(Infrastructure5xxException exception) {
        log.error("[{}] path={} | message={}", INFRASTRUCTURE_5XX_ERROR.formatted(), exception.getUriPath(), exception.getMessage(), exception);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .path(exception.getUriPath())
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
                .errorCode(INFRASTRUCTURE_5XX_ERROR.getInternalCode())
                .timestamp(OffsetDateTime.now())
                .message(INFRASTRUCTURE_5XX_ERROR.getErrorMessage())
                .subErrors(exception.getSubErrors())
                .build();

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
    }
}
