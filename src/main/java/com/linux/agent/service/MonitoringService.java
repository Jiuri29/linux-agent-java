package com.linux.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linux.agent.audit.AuditService;
import com.linux.agent.domain.AlertRule;
import com.linux.agent.monitor.OsCollectors;
import com.linux.agent.repository.MonitorRepository;
import com.linux.agent.security.Tokens;
import com.linux.agent.web.AppException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MonitoringService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final OsCollectors collectors;    private final MonitorRepository repository;
    private final Tokens tokens;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public MonitoringService(OsCollectors collectors, MonitorRepository repository,
                             Tokens tokens, ObjectMapper mapper, AuditService audit) {
        this.collectors = collectors;
        this.repository = repository;
        this.tokens = tokens;
        this.mapper = mapper;
        this.audit = audit;
    }

    public Map<String, Object> readOnlySnapshot() {
        return collectors.readOnlySnapshot();
    }

    public synchronized Map<String, Object> captureAndEvaluate() {
        Map<String, Object> snapshot = collectors.readOnlySnapshot();
        String host = String.valueOf(snapshot.get("hostname"));
        Map<String, Double> flat = flatten(snapshot);
        double maxDiskUsage = 0;
        if (snapshot.get("disks") instanceof List<?> disks) {
            for (Object disk : disks) {
                if (disk instanceof Map<?, ?> d && d.get("usage_percent") instanceof Number n) {
                    maxDiskUsage = Math.max(maxDiskUsage, n.doubleValue());
                }
            }
        }
        flat.put("disk.usage_percent_max", maxDiskUsage);

        Map<String, Object> document = new LinkedHashMap<>(snapshot);
        document.put("metrics", flat);
        repository.writeLatest(document);
        Map<String, Object> metricEntry = new LinkedHashMap<>();
        metricEntry.put("ts", Instant.now().toString());
        metricEntry.put("host", host);
        metricEntry.put("metrics", flat);
        repository.appendMetric(metricEntry);

        List<Map<String, Object>> fired = evaluate(flat, host, false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshot", document);
        result.put("alerts_fired", fired.size());
        return result;
    }

    public Map<String, Object> latest() {
        return repository.latestExists()
                ? repository.readLatest(MAP_TYPE)
                : null;
    }

    public List<Map<String, Object>> history(String host, String metric, int limit) {
        List<String> lines = repository.readMetricLines();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && result.size() < limit; i--) {
            try {
                Map<String, Object> entry = mapper.readValue(lines.get(i), MAP_TYPE);
                if (host != null && !host.equals(entry.get("host"))) {
                    continue;
                }
                if (metric != null) {
                    Map<String, Object> metrics = castMap(entry.get("metrics"));
                    boolean containsMetric = metrics.keySet().stream()
                            .anyMatch(k -> k.contains(metric));
                    if (!containsMetric) {
                        continue;
                    }
                }
                result.add(entry);
            } catch (Exception ignored) {
                // skip malformed history line
            }
        }
        return result;
    }

    public List<AlertRule> rules() {
        return repository.loadRules();
    }

    public Map<String, Object> saveRules(Map<String, Object> body) {
        List<AlertRule> rules = repository.loadRules();
        if (Boolean.TRUE.equals(body.get("reset"))) {
            repository.saveRules(new ArrayList<>());
            audit.append("monitor", "system", "alert-rules-reset", "rules", "success", Map.of());
            return Map.of("status", "ok", "reset", true);
        }
        Object rawRule = body.get("rule");
        if (rawRule == null) {
            throw AppException.validation("rule is required");
        }
        AlertRule rule = mapper.convertValue(rawRule, AlertRule.class);
        if (rule.metric == null || rule.metric.isBlank()) {
            throw AppException.validation("rule.metric is required");
        }
        if (!List.of(">=", ">", "<=", "<", "==", "!=").contains(rule.operator)) {
            throw AppException.validation("rule.operator must be one of >= > <= < == !=");
        }
        if (rule.severity == null || rule.severity.isBlank()) {
            rule.severity = "warning";
        }
        if (rule.id == null || rule.id.isBlank()) {
            rule.id = "alr-" + tokens.shortId();
        }
        rules.removeIf(r -> r.id.equals(rule.id));
        rules.add(rule);
        repository.saveRules(rules);
        audit.append("monitor", "system", "alert-rule-save", "rule:" + rule.id, "success",
                Map.of("metric", rule.metric, "operator", rule.operator, "threshold", rule.threshold));
        return Map.of("status", "ok", "id", rule.id);
    }

    public List<Map<String, Object>> simulate(Map<String, Object> body) {
        Map<String, Double> flat = extractMetrics(body);
        return evaluate(flat, "simulated-host", true);
    }

    public List<MonitorRepository.Alert> alerts(String status) {
        List<MonitorRepository.Alert> alerts = repository.loadState().alerts;
        if (status == null || status.isBlank()) {
            return List.copyOf(alerts);
        }
        return alerts.stream().filter(a -> status.equalsIgnoreCase(a.status)).toList();
    }

    public MonitorRepository.Alert transition(String alertId, String action) {
        MonitorRepository.MonitorState state = repository.loadState();
        MonitorRepository.Alert target = state.alerts.stream()
                .filter(a -> a.id.equals(alertId))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("alert_not_found"));
        switch (action) {
            case "acknowledge" -> target.status = "acknowledged";
            case "escalate" -> target.status = "escalated";
            case "recover" -> target.status = "recovered";
            default -> throw AppException.validation("action must be acknowledge, escalate or recover");
        }
        target.updatedAt = Instant.now();
        addNotification(state, "info", "alert " + alertId + " -> " + action);
        repository.saveState(state);
        audit.append("monitor", "system", "alert-" + action, "alert:" + alertId, "success", Map.of());
        return target;
    }

    public List<MonitorRepository.Suppression> suppressions() {
        return List.copyOf(repository.loadState().suppressions);
    }

    public MonitorRepository.Suppression addSuppression(Map<String, Object> body) {
        MonitorRepository.MonitorState state = repository.loadState();
        MonitorRepository.Suppression suppression = new MonitorRepository.Suppression();
        suppression.id = "sup-" + tokens.shortId();
        suppression.host = str(body.get("host"));
        suppression.metric = str(body.get("metric"));
        suppression.reason = str(body.get("reason"));
        Object until = body.get("until_epoch_ms");
        if (until instanceof Number number) {
            suppression.untilEpochMs = number.longValue();
        }
        state.suppressions.add(suppression);
        repository.saveState(state);
        return suppression;
    }

    public List<MonitorRepository.Notification> notifications() {
        return List.copyOf(repository.loadState().notifications);
    }

    public Map<String, Object> riskScan() {
        List<Map<String, Object>> findings = new ArrayList<>();
        Map<String, Object> latest = latest();
        if (latest != null && latest.get("metrics") instanceof Map) {
            Map<String, Object> metrics = castMap(latest.get("metrics"));
            if (metrics.get("cpu.usage_percent") instanceof Number c && c.doubleValue() >= 90) {
                findings.add(Map.of("level", "critical", "type", "cpu_hot", "value", c.doubleValue()));
            }
            if (metrics.get("memory.usage_percent") instanceof Number m && m.doubleValue() >= 85) {
                findings.add(Map.of("level", "warning", "type", "memory_pressure", "value", m.doubleValue()));
            }
            if (metrics.get("disk.usage_percent_max") instanceof Number d && d.doubleValue() >= 85) {
                findings.add(Map.of("level", "warning", "type", "disk_pressure", "value", d.doubleValue()));
            }
        }
        findings.add(Map.of("level", "info", "type", "scan_completed",
                "detail", "basic local heuristics only"));
        return Map.of("checked_at", Instant.now().toString(), "findings", findings);
    }

    private List<Map<String, Object>> evaluate(Map<String, Double> flat, String host, boolean dryRun) {
        List<Map<String, Object>> fired = new ArrayList<>();
        List<AlertRule> rules = repository.loadRules();

        if (dryRun) {
            for (AlertRule rule : rules) {
                if (!rule.enabled) {
                    continue;
                }
                Double value = flat.get(rule.metric);
                if (value != null && breach(value, rule)) {
                    fired.add(candidate(rule, value));
                }
            }
            return fired;
        }

        MonitorRepository.MonitorState state = repository.loadState();
        boolean changed = false;
        for (AlertRule rule : rules) {
            if (!rule.enabled || suppressed(state.suppressions, host, rule.metric)) {
                continue;
            }
            Double value = flat.get(rule.metric);
            if (value == null) {
                continue;
            }
            MonitorRepository.Alert open = state.alerts.stream()
                    .filter(a -> a.ruleId.equals(rule.id) && a.host.equals(host))
                    .filter(a -> List.of("active", "acknowledged", "escalated").contains(a.status))
                    .findFirst()
                    .orElse(null);
            if (breach(value, rule)) {
                if (open == null) {
                    MonitorRepository.Alert alert = new MonitorRepository.Alert();
                    alert.id = "ald-" + tokens.shortId();
                    alert.ruleId = rule.id;
                    alert.host = host;
                    alert.metric = rule.metric;
                    alert.value = value;
                    alert.severity = rule.severity;
                    alert.message = rule.name + ": " + rule.metric + " " + rule.operator + " "
                            + rule.threshold + " (current " + value + ")";
                    alert.firedAt = Instant.now();
                    alert.updatedAt = alert.firedAt;
                    state.alerts.add(alert);
                    addNotification(state, rule.severity, "alert fired: " + alert.message);
                    fired.add(toMap(alert));
                } else {
                    open.value = value;
                    open.updatedAt = Instant.now();
                }
                changed = true;
            } else if (open != null) {
                open.status = "recovered";
                open.updatedAt = Instant.now();
                addNotification(state, "info", "alert recovered: " + open.message);
                changed = true;
            }
        }
        if (changed) {
            repository.saveState(state);
        }
        return fired;
    }

    private boolean suppressed(List<MonitorRepository.Suppression> suppressions, String host, String metric) {
        long now = System.currentTimeMillis();
        return suppressions.stream().anyMatch(s ->
                (s.untilEpochMs == null || s.untilEpochMs > now)
                        && (s.host == null || s.host.equals(host))
                        && (s.metric == null || s.metric.equals(metric)));
    }

    private boolean breach(double value, AlertRule rule) {
        return switch (rule.operator) {
            case ">" -> value > rule.threshold;
            case "<=" -> value <= rule.threshold;
            case "<" -> value < rule.threshold;
            case "==" -> value == rule.threshold;
            case "!=" -> value != rule.threshold;
            default -> value >= rule.threshold;
        };
    }

    private Map<String, Object> candidate(AlertRule rule, double value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rule_id", rule.id);
        map.put("name", rule.name);
        map.put("metric", rule.metric);
        map.put("operator", rule.operator);
        map.put("threshold", rule.threshold);
        map.put("value", value);
        map.put("severity", rule.severity);
        return map;
    }

    private Map<String, Double> extractMetrics(Map<String, Object> body) {
        Object direct = body.get("metrics");
        if (direct instanceof Map) {
            return toStringDoubleMap(direct);
        }
        Object snapshot = body.get("snapshot");
        if (snapshot instanceof Map<?, ?> snap && snap.get("metrics") instanceof Map) {
            return toStringDoubleMap(snap.get("metrics"));
        }
        Map<String, Double> flat = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (entry.getValue() instanceof Number number) {
                flat.put(entry.getKey(), number.doubleValue());
            }
        }
        if (flat.isEmpty()) {
            throw AppException.validation("provide metrics map for simulation");
        }
        return flat;
    }

    private Map<String, Double> flatten(Map<String, Object> source) {
        Map<String, Double> flat = new LinkedHashMap<>();
        walk(source, "", flat);
        return flat;
    }

    private void walk(Map<String, Object> node, String prefix, Map<String, Double> out) {
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Number number) {
                out.put(key, number.doubleValue());
            } else if (value instanceof Map) {
                walk(castMap(value), key, out);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> toStringDoubleMap(Object raw) {
        Map<String, Object> source = castMap(raw);
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getValue() instanceof Number number) {
                result.put(entry.getKey(), number.doubleValue());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object raw) {
        if (!(raw instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) raw;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void addNotification(MonitorRepository.MonitorState state, String level, String message) {
        MonitorRepository.Notification notification = new MonitorRepository.Notification();
        notification.ts = Instant.now();
        notification.level = level;
        notification.message = message;
        state.notifications.add(notification);
        if (state.notifications.size() > 500) {
            state.notifications.removeAll(state.notifications.subList(0, state.notifications.size() - 500));
        }
    }

    private Map<String, Object> toMap(MonitorRepository.Alert alert) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", alert.id);
        map.put("rule_id", alert.ruleId);
        map.put("host", alert.host);
        map.put("metric", alert.metric);
        map.put("value", alert.value);
        map.put("severity", alert.severity);
        map.put("status", alert.status);
        map.put("message", alert.message);
        map.put("fired_at", alert.firedAt == null ? null : alert.firedAt.toString());
        map.put("updated_at", alert.updatedAt == null ? null : alert.updatedAt.toString());
        return map;
    }
}
