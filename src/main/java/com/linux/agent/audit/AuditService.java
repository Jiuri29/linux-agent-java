package com.linux.agent.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linux.agent.config.AppProperties;
import com.linux.agent.repository.JsonFileStore;
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
                throw new com.linux.agent.web.AppException(
                        com.linux.agent.web.ErrorCode.AUDIT_READ_FAILED, "failed to parse audit log");
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

    public long totalEvents() {
        return store.readLines(java.nio.file.Path.of(logPath)).size();
    }

    public List<AuditEvent> readAllEvents() {
        return parseAll();
    }

    private List<AuditEvent> parseAll() {
        List<AuditEvent> events = new java.util.ArrayList<>();
        for (String line : store.readLines(java.nio.file.Path.of(logPath))) {
            try {
                events.add(mapper.readValue(line, EVENT_TYPE));
            } catch (Exception ignored) {
                // skip malformed audit line
            }
        }
        return events;
    }

    public List<AuditEvent> query(String user, String asset, String action, String risk,
                                  String eventType, String keyword, String from, String to) {
        Instant fromTs = parseInstant(from);
        Instant toTs = parseInstant(to);
        List<AuditEvent> result = new java.util.ArrayList<>();
        for (AuditEvent event : readAllEvents()) {
            if (user != null && !user.equalsIgnoreCase(event.actor())) {
                continue;
            }
            if (asset != null && (event.target() == null || !event.target().contains(asset))) {
                continue;
            }
            if (action != null && (event.action() == null
                    || !event.action().toLowerCase().contains(action.toLowerCase()))) {
                continue;
            }
            if (risk != null && (event.result() == null || !risk.equalsIgnoreCase(event.result()))) {
                continue;
            }
            if (eventType != null && (event.eventType() == null
                    || !eventType.equalsIgnoreCase(event.eventType()))) {
                continue;
            }
            if (keyword != null && !keywordHit(event, keyword)) {
                continue;
            }
            if (fromTs != null && parseInstant(event.ts()) != null && parseInstant(event.ts()).isBefore(fromTs)) {
                continue;
            }
            if (toTs != null && parseInstant(event.ts()) != null && parseInstant(event.ts()).isAfter(toTs)) {
                continue;
            }
            result.add(event);
        }
        result.sort((a, b) -> Long.compare(b.seq(), a.seq()));
        return result;
    }

    public String exportText(String user, String asset, String action, String risk,
                             String eventType, String keyword, String from, String to) {
        StringBuilder sb = new StringBuilder();
        for (AuditEvent event : query(user, asset, action, risk, eventType, keyword, from, to)) {
            try {
                sb.append(mapper.writeValueAsString(event)).append(System.lineSeparator());
            } catch (Exception ignored) {
                // skip unparsable event during export
            }
        }
        return sb.toString();
    }

    public synchronized Map<String, Object> archive(int days) {
        Instant threshold = Instant.now().minus(java.time.Duration.ofDays(days));
        java.nio.file.Path path = java.nio.file.Path.of(logPath);
        List<String> lines = store.readLines(path);
        List<String> kept = new java.util.ArrayList<>();
        List<String> archived = new java.util.ArrayList<>();
        for (String line : lines) {
            try {
                AuditEvent event = mapper.readValue(line, EVENT_TYPE);
                Instant ts = parseInstant(event.ts());
                if (ts != null && ts.isBefore(threshold)) {
                    archived.add(line);
                } else {
                    kept.add(line);
                }
            } catch (Exception e) {
                kept.add(line);
            }
        }
        int archivedCount = archived.size();
        if (archivedCount > 0) {
            String stamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            java.nio.file.Path archiveFile = path.getParent().resolve(
                    "events-archive-" + stamp + ".jsonl");
            try {
                java.nio.file.Files.write(archiveFile,
                        String.join(System.lineSeparator(), archived).getBytes(StandardCharsets.UTF_8));
                java.nio.file.Files.write(path,
                        String.join(System.lineSeparator(), kept).getBytes(StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                throw new com.linux.agent.web.AppException(
                        com.linux.agent.web.ErrorCode.AUDIT_READ_FAILED, "failed to write archive");
            }
            lastHash = null;
            lastSeq = 0;
            ensureTail();
        }
        return Map.of("archived", archivedCount, "kept", kept.size(), "retention_days", days);
    }

    public Map<String, Object> forensics() {
        List<AuditEvent> all = readAllEvents();
        Map<String, long[]> byActor = new java.util.LinkedHashMap<>();
        Map<String, Long> byEventType = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> recentFailures = new java.util.ArrayList<>();
        for (AuditEvent event : all) {
            long[] counts = byActor.computeIfAbsent(event.actor() == null ? "unknown" : event.actor(),
                    k -> new long[2]);
            counts[0]++;
            boolean failure = event.result() != null
                    && List.of("failure", "blocked").contains(event.result());
            if (failure) {
                counts[1]++;
            }
            byEventType.merge(event.eventType() == null ? "unknown" : event.eventType(), 1L, Long::sum);
            if (failure && recentFailures.size() < 20) {
                recentFailures.add(Map.of(
                        "seq", event.seq(),
                        "ts", event.ts(),
                        "actor", String.valueOf(event.actor()),
                        "action", String.valueOf(event.action()),
                        "target", String.valueOf(event.target()),
                        "result", String.valueOf(event.result())));
            }
        }
        List<Map<String, Object>> actors = new java.util.ArrayList<>();
        byActor.forEach((actor, counts) -> actors.add(Map.of(
                "actor", actor, "total", counts[0], "failures", counts[1])));
        List<Map<String, Object>> types = new java.util.ArrayList<>();
        byEventType.forEach((type, count) -> types.add(Map.of("event_type", type, "total", count)));
        return Map.of("by_actor", actors, "by_event_type", types,
                "recent_failures", recentFailures);
    }

    public Map<String, Object> compliance(int days) {
        Instant since = Instant.now().minus(java.time.Duration.ofDays(days));
        long total = 0;
        long failures = 0;
        long blocked = 0;
        long failedLogins = 0;
        for (AuditEvent event : readAllEvents()) {
            Instant ts = parseInstant(event.ts());
            if (ts == null || ts.isBefore(since)) {
                continue;
            }
            total++;
            if ("failure".equals(event.result())) {
                failures++;
                if ("auth".equals(event.eventType()) && "login".equals(event.action())) {
                    failedLogins++;
                }
            }
            if ("blocked".equals(event.result())) {
                blocked++;
            }
        }
        Map<String, Object> totals = new java.util.LinkedHashMap<>();
        totals.put("events", total);
        totals.put("failures", failures);
        totals.put("blocked_actions", blocked);
        totals.put("logins_failed", failedLogins);
        Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("generated_at", Instant.now().toString());
        report.put("period_days", days);
        report.put("totals", totals);
        report.put("integrity", verifyChain());
        return report;
    }

    public Map<String, Object> retention(int days) {
        return Map.of("days", days, "events", totalEvents());
    }

    private static boolean keywordHit(AuditEvent event, String keyword) {
        String needle = keyword.toLowerCase();
        for (String field : new String[]{event.actor(), event.action(), event.target(),
                event.eventType(), event.result()}) {
            if (field != null && field.toLowerCase().contains(needle)) {
                return true;
            }
        }
        if (event.detail() != null) {
            try {
                return new ObjectMapper().writeValueAsString(event.detail()).toLowerCase().contains(needle);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return false;
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
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
