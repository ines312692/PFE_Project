package com.elyadata.common.exceptions.infrastructure;

import com.elyadata.common.exceptions.SubError;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
public class Infrastructure4xxException extends InfrastructureException {

    private final Set<SubError> subErrors;

    @Builder(builderMethodName = "infrastructure4xxExceptionBuilder")
    public Infrastructure4xxException(String message, String uriPath, Set<SubError> subErrors) {
        super(message, uriPath);
        this.subErrors = subErrors;
    }
}