package com.kylin.agent.audit;

import java.util.Map;

public record AuditEvent(
        long seq,
        String ts,
        String eventType,
        String actor,
        String action,
        String target,
        String result,
        Map<String, Object> detail,
        String prevHash,
        String hash) {
}
