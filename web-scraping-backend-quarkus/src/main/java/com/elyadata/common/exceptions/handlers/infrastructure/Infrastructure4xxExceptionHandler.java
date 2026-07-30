package com.elyadata.common.exceptions.handlers.infrastructure;

import com.elyadata.common.exceptions.ErrorResponse;
import com.elyadata.common.exceptions.infrastructure.Infrastructure4xxException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;

import static com.elyadata.common.utils.InternalErrors.INFRASTRUCTURE_4XX_ERROR;

@Slf4j
@Provider
public class Infrastructure4xxExceptionHandler implements ExceptionMapper<Infrastructure4xxException> {

    @Override
    public Response toResponse(Infrastructure4xxException exception) {
        log.warn("[{}] path={} | message={}", INFRASTRUCTURE_4XX_ERROR.formatted(), exception.getUriPath(), exception.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .path(exception.getUriPath())
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .errorCode(INFRASTRUCTURE_4XX_ERROR.getInternalCode())
                .timestamp(OffsetDateTime.now())
                .message(INFRASTRUCTURE_4XX_ERROR.getErrorMessage())
                .subErrors(exception.getSubErrors())
                .build();

        return Response.status(Response.Status.BAD_REQUEST).entity(errorResponse).build();
    }
}
