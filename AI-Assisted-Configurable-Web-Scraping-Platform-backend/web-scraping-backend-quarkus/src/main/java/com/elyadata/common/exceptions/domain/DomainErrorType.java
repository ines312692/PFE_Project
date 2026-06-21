package com.elyadata.common.exceptions.domain;

import com.elyadata.common.utils.InternalErrors;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DomainErrorType {

    NOT_FOUND(
            InternalErrors.RESOURCE_NOT_FOUND,
            Response.Status.NOT_FOUND
    ),
    ALREADY_EXISTS(
            InternalErrors.ALREADY_EXIST_ENTITY,
            Response.Status.CONFLICT
    ),
    NOT_VALID(
            InternalErrors.NOT_VALID_ENTITY,
            Response.Status.BAD_REQUEST
    ),
    UNAUTHORIZED(
            InternalErrors.UNAUTHORIZED_ACCESS,
            Response.Status.UNAUTHORIZED
    ),
    INVALID_EXTRACTION_MODE(
            InternalErrors.NOT_VALID_ENTITY,
            Response.Status.BAD_REQUEST
    ),
    EXPORT_FAILURE(
            InternalErrors.DOMAIN_ERROR,
            Response.Status.INTERNAL_SERVER_ERROR
    ),
    GENERIC(
            InternalErrors.DOMAIN_ERROR,
            Response.Status.fromStatusCode(422)
    );

    private final InternalErrors internalError;
    private final Response.Status httpStatus;
}