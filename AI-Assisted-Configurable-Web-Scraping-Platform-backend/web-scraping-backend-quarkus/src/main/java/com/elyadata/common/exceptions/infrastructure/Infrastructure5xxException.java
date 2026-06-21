package com.elyadata.common.exceptions.infrastructure;

import com.elyadata.common.exceptions.SubError;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
public class Infrastructure5xxException extends InfrastructureException {

    private final Set<SubError> subErrors;

    @Builder(builderMethodName = "infrastructure5xxExceptionBuilder")
    public Infrastructure5xxException(String message, String uriPath, Set<SubError> subErrors) {
        super(message, uriPath);
        this.subErrors = subErrors;
    }
}