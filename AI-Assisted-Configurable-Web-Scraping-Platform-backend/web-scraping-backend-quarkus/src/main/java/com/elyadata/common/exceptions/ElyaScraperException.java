package com.elyadata.common.exceptions;

import lombok.Getter;

@Getter
public class ElyaScraperException extends RuntimeException {

    private final String uriPath;

    public ElyaScraperException(String message, String uriPath) {
        super(message);
        this.uriPath = uriPath;
    }
}