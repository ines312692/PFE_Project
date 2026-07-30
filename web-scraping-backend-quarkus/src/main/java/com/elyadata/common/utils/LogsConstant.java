package com.elyadata.common.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class LogsConstant {

    @UtilityClass
    public static class Keys {
        public static final String EXCEPTION_MESSAGE = "EXCEPTION_MESSAGE";
        public static final String STACK_TRACE = "STACK_TRACE";
        public static final String JOB_ID = "JOB_ID";
        public static final String USER_ID = "USER_ID";
        public static final String REQUEST_ID = "REQUEST_ID";
        public static final String PIPELINE_ID = "PIPELINE_ID";
        public static final String PROXY_ID = "PROXY_ID";
    }

    @UtilityClass
    public static class Job {
        public static final String CREATED = "JOB_CREATED";
        public static final String UPDATED = "JOB_UPDATED";
        public static final String DELETED = "JOB_DELETED";
        public static final String LAUNCHED = "JOB_LAUNCHED";
        public static final String PAUSED = "JOB_PAUSED";
        public static final String RESUMED = "JOB_RESUMED";
        public static final String COMPLETED = "JOB_COMPLETED";
        public static final String FAILED = "JOB_FAILED";
        public static final String RETRYING = "JOB_RETRYING";
    }

    @UtilityClass
    public static class Schema {
        public static final String CREATED = "SCHEMA_CREATED";
        public static final String UPDATED = "SCHEMA_UPDATED";
        public static final String DELETED = "SCHEMA_DELETED";
        public static final String VALIDATED = "SCHEMA_VALIDATED";
        public static final String INVALID = "SCHEMA_VALIDATION_FAILED";
    }

    @UtilityClass
    public static class Proxy {
        public static final String ADDED = "PROXY_ADDED";
        public static final String REMOVED = "PROXY_REMOVED";
        public static final String ROTATED = "PROXY_ROTATED";
        public static final String BLACKLISTED = "PROXY_BLACKLISTED";
        public static final String HEALTH_OK = "PROXY_HEALTH_CHECK_PASSED";
        public static final String HEALTH_FAIL = "PROXY_HEALTH_CHECK_FAILED";
        public static final String POOL_EMPTY = "PROXY_POOL_EXHAUSTED";
    }

    @UtilityClass
    public static class Pipeline {
        public static final String STARTED = "PIPELINE_STARTED";
        public static final String COMPLETED = "PIPELINE_COMPLETED";
        public static final String FAILED = "PIPELINE_FAILED";
        public static final String STEP_STARTED = "PIPELINE_STEP_STARTED";
        public static final String STEP_COMPLETED = "PIPELINE_STEP_COMPLETED";
        public static final String STEP_FAILED = "PIPELINE_STEP_FAILED";
    }

    @UtilityClass
    public static class Auth {
        public static final String LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
        public static final String LOGIN_FAILED = "AUTH_LOGIN_FAILED";
        public static final String LOGOUT = "AUTH_LOGOUT";
        public static final String TOKEN_REFRESHED = "AUTH_TOKEN_REFRESHED";
        public static final String TOKEN_EXPIRED = "AUTH_TOKEN_EXPIRED";
        public static final String UNAUTHORIZED = "AUTH_UNAUTHORIZED_ACCESS";
    }

    @UtilityClass
    public static class Http {
        public static final String REQUEST = "HTTP_INCOMING_REQUEST";
        public static final String RESPONSE = "HTTP_OUTGOING_RESPONSE";
        public static final String ERROR = "HTTP_REQUEST_ERROR";
    }
}
