package com.elyadata.common.exceptions.domain;

import com.elyadata.common.exceptions.ElyaScraperException;
import com.elyadata.common.exceptions.SubError;
import lombok.Getter;

import java.util.Set;

@Getter
public class DomainException extends ElyaScraperException {

    private final DomainErrorType type;
    private final transient Set<SubError> subErrors;

    private DomainException(DomainErrorType type,
                            String message,
                            String uriPath,
                            Set<SubError> subErrors) {
        super(message, uriPath);
        this.type = type;
        this.subErrors = subErrors != null ? subErrors : Set.of();
    }

    public static DomainException notFound(String message, String path) {
        return new DomainException(DomainErrorType.NOT_FOUND, message, path, Set.of());
    }

    public static DomainException notFound(String message, String path, Set<SubError> subErrors) {
        return new DomainException(DomainErrorType.NOT_FOUND, message, path, subErrors);
    }

    public static DomainException unauthorized(String message, String path) {
        return new DomainException(DomainErrorType.UNAUTHORIZED, message, path, Set.of());
    }

    public static DomainException notFound(String message) {
        return new DomainException(DomainErrorType.NOT_FOUND, message, null, Set.of());
    }

    public static DomainException alreadyExists(String message) {
        return new DomainException(DomainErrorType.ALREADY_EXISTS, message, null, Set.of());
    }

    public static DomainException notValid(String message) {
        return new DomainException(DomainErrorType.NOT_VALID, message, null, Set.of());
    }

    public static DomainException invalidExtractionMode(String message) {
        return new DomainException(DomainErrorType.INVALID_EXTRACTION_MODE, message, null, Set.of());
    }

    public static DomainException exportFailure(String message) {
        return new DomainException(DomainErrorType.EXPORT_FAILURE, message, null, Set.of());
    }
}