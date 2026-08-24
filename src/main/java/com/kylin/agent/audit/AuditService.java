package com.kylin.agent.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kylin.agent.config.AppProperties;
import com.kylin.agent.repository.JsonFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AuditService {

    public static final String GENESIS = "GENESIS";
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final TypeReference<AuditEvent> EVENT_TYPE = new TypeReference<>() {
    };

    private final JsonFileStore store;
    private final ObjectMapper mapper;
    private final String logPath;
    private final Object lock = new Object();

    private String lastHash;
    private long lastSeq;

    public AuditService(JsonFileStore store, ObjectMapper mapper, AppProperties properties) {
        this.store = store;
        this.mapper = mapper;
        this.logPath = properties.auditLog();
    }

    public AuditEvent append(String eventType, String actor, String action, String target,
                             String result, Map<String, Object> detail) {
        synchronized (lock) {
            ensureTail();
            lastSeq += 1;
            String prevHash = lastHash == null ? GENESIS : lastHash;
            String ts = Instant.now().toString();
            Map<String, Object> safeDetail = detail == null ? Map.of() : new TreeMap<>(detail);
            String canonical = canonical(lastSeq, ts, eventType, actor, action, target, result, safeDetail, prevHash);
            String hash = sha256Hex(canonical);
            AuditEvent event = new AuditEvent(lastSeq, ts, eventType, actor, action, target, result,
                    safeDetail.isEmpty() ? null : safeDetail, prevHash, hash);
            store.appendLine(java.nio.file.Path.of(logPath), event);
            lastHash = hash;
            return event;
        }
    }

    public List<AuditEvent> recent(int limit) {
        List<String> lines = store.readLines(java.nio.file.Path.of(logPath));
        int from = Math.max(0, lines.size() - limit);
        List<AuditEvent> events = new java.util.ArrayList<>();
        for (int i = lines.size() - 1; i >= from; i--) {
            try {
                events.add(mapper.readValue(lines.get(i), EVENT_TYPE));
            } catch (Exception e) {
                throw new com.kylin.agent.web.AppException(
                        com.kylin.agent.web.ErrorCode.AUDIT_READ_FAILED, "failed to parse audit log");
            }
        }
        return events;
    }

    public Map<String, Object> verifyChain() {
        List<String> lines = store.readLines(java.nio.file.Path.of(logPath));
        String prevHash = GENESIS;
        long expectedSeq = 0;
        for (String line : lines) {
            AuditEvent event;
            try {
                event = mapper.readValue(line, EVENT_TYPE);
            } catch (Exception e) {
                return Map.of("valid", false, "checked", expectedSeq, "reason", "unparsable line");
            }
            expectedSeq++;
            if (event.seq() != expectedSeq || !GENESIS.equals(prevHash) && !prevHash.equals(event.prevHash())) {
                return Map.of("valid", false, "checked", expectedSeq - 1, "reason", "chain mismatch");
            }
            String canonical = canonical(event.seq(), event.ts(), event.eventType(), event.actor(),
                    event.action(), event.target(), event.result(),
                    event.detail() == null ? Map.of() : new TreeMap<>(event.detail()), event.prevHash());
            if (!sha256Hex(canonical).equals(event.hash())) {
                return Map.of("valid", false, "checked", expectedSeq - 1, "reason", "hash mismatch");
            }
            prevHash = event.hash();
        }
        return Map.of("valid", true, "checked", expectedSeq);
    }

    private void ensureTail() {
        if (lastHash != null) {
            return;
        }
        List<String> lines = store.readLines(java.nio.file.Path.of(logPath));
        if (lines.isEmpty()) {
            return;
        }
        try {
            AuditEvent tail = mapper.readValue(lines.get(lines.size() - 1), EVENT_TYPE);
            lastHash = tail.hash();
            lastSeq = tail.seq();
        } catch (Exception e) {
            log.warn("failed to resume audit chain from {}", logPath, e);
        }
    }

    private String canonical(long seq, String ts, String eventType, String actor, String action,
                             String target, String result, Map<String, Object> detail, String prevHash) {
        return seq + "|" + ts + "|" + eventType + "|" + actor + "|" + action + "|" + target
                + "|" + result + "|" + toJson(detail) + "|" + prevHash;
    }

    private String toJson(Map<String, Object> detail) {
        if (detail.isEmpty()) {
            return "{}";
        }
        try {
            return mapper.writeValueAsString(detail);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
