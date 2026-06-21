package com.elyadata.common.exceptions.infrastructure;

import com.elyadata.common.exceptions.ElyaScraperException;
import lombok.Builder;

public class InfrastructureException extends ElyaScraperException {

    @Builder(builderMethodName = "infrastructureExceptionBuilder")
    public InfrastructureException(String message, String uriPath) {
        super(message, uriPath);
    }
}