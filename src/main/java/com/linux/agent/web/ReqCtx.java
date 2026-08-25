package com.linux.agent.web;

import java.util.Optional;

public final class ReqCtx {

    public record Ctx(String requestId, String clientIp, String method, String path) {
    }

    private static final ThreadLocal<Ctx> HOLDER = new ThreadLocal<>();

    private ReqCtx() {
    }

    public static void set(Ctx ctx) {
        HOLDER.set(ctx);
    }

    public static Optional<Ctx> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static Optional<String> requestId() {
        Ctx ctx = HOLDER.get();
        return ctx == null ? Optional.empty() : Optional.ofNullable(ctx.requestId());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
