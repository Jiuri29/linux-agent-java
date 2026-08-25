package com.linux.agent.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.linux.agent.config.AppProperties;
import com.linux.agent.domain.AlertRule;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MonitorRepository {

    private static final TypeReference<List<AlertRule>> RULES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<MonitorState> STATE_TYPE = new TypeReference<>() {
    };

    public static class Alert {
        public String id;
        @JsonProperty("rule_id")
        public String ruleId;
        public String host;
        public String metric;
        public double value;
        public String severity;
        public String status = "active";
        public String message;
        @JsonProperty("fired_at")
        public Instant firedAt;
        @JsonProperty("updated_at")
        public Instant updatedAt;
    }

    public static class Suppression {
        public String id;
        public String host;
        public String metric;
        public String reason;
        @JsonProperty("until_epoch_ms")
        public Long untilEpochMs;
    }

    public static class Notification {
        @JsonProperty("ts")
        public Instant ts;
        public String level;
        public String message;
    }

    public static class MonitorState {
        public List<Alert> alerts = new ArrayList<>();
        public List<Suppression> suppressions = new ArrayList<>();
        public List<Notification> notifications = new ArrayList<>();
    }

    private final JsonFileStore store;
    private final Path rulesPath;
    private final Path statePath;
    private final Path metricsPath;
    private final Path latestPath;

    public MonitorRepository(JsonFileStore store, AppProperties properties) {
        this.store = store;
        this.rulesPath = Path.of(properties.alertRulesConfig());
        this.statePath = Path.of(properties.alertStateStore());
        this.metricsPath = Path.of(properties.metricsHistoryStore());
        this.latestPath = Path.of(properties.latestSnapshotStore());
    }

    public List<AlertRule> loadRules() {
        return store.read(rulesPath, RULES_TYPE, ArrayList::new);
    }

    public void saveRules(List<AlertRule> rules) {
        store.write(rulesPath, rules);
    }

    public MonitorState loadState() {
        return store.read(statePath, STATE_TYPE, MonitorState::new);
    }

    public void saveState(MonitorState state) {
        store.write(statePath, state);
    }

    public void appendMetric(Object metricsEntry) {
        store.appendLine(metricsPath, metricsEntry);
    }

    public List<String> readMetricLines() {
        return store.readLines(metricsPath);
    }

    public void writeLatest(Object snapshot) {
        store.write(latestPath, snapshot);
    }

    public <T> T readLatest(com.fasterxml.jackson.core.type.TypeReference<T> type) {
        return store.read(latestPath, type, () -> null);
    }

    public boolean latestExists() {
        return java.nio.file.Files.exists(latestPath);
    }
}
