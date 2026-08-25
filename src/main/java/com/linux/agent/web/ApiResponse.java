package com.linux.agent.web;

import java.util.Map;

public record ApiResponse(
        boolean ok,
        String code,
        String message,
        Object data,
        Map<String, Object> meta,
        String requestId) {

    public static ApiResponse ok(Object data) {
        return new ApiResponse(true, ErrorCode.OK.value(), "ok", data, null, ReqCtx.requestId().orElse(null));
    }

    public static ApiResponse ok(Object data, Map<String, Object> meta) {
        return new ApiResponse(true, ErrorCode.OK.value(), "ok", data, meta, ReqCtx.requestId().orElse(null));
    }

    public static ApiResponse error(ErrorCode code, String message) {
        return error(code, message, null);
    }

    public static ApiResponse error(ErrorCode code, String message, Object data) {
        return new ApiResponse(false, code.value(), message, data, null, ReqCtx.requestId().orElse(null));
    }

    public static ApiResponse from(AppException e) {
        return new ApiResponse(false, e.code().value(), e.getMessage(),
                e.data().isEmpty() ? null : e.data(), null, ReqCtx.requestId().orElse(null));
    }
}
