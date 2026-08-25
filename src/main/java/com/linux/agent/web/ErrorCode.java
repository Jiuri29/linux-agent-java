package com.linux.agent.web;

public enum ErrorCode {
    OK("ok", 200),
    VALIDATION_ERROR("validation_error", 400),
    NOT_FOUND("not_found", 404),
    UNAUTHENTICATED("unauthenticated", 401),
    PERMISSION_DENIED("permission_denied", 403),
    CSRF_REQUIRED("csrf_required", 403),
    INVALID_AGENT_API_CONFIG("invalid_agent_api_config", 400),
    AUDIT_READ_FAILED("audit_read_failed", 500),
    INTERNAL_ERROR("internal_error", 500);

    private final String value;
    private final int httpStatus;

    ErrorCode(String value, int httpStatus) {
        this.value = value;
        this.httpStatus = httpStatus;
    }

    public String value() {
        return value;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
