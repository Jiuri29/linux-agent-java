package com.linux.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linux.agent.audit.AuditService;
import com.linux.agent.config.AppProperties;
import com.linux.agent.monitor.OsCollectors;
import com.linux.agent.repository.AssetRepository;
import com.linux.agent.repository.JsonFileStore;
import com.linux.agent.security.TerminalPolicy;
import com.linux.agent.web.AppException;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class AcceptanceService {

    public record CheckResult(String id, String name, String category,
                              String status, long duration_ms, String message) {
    }

    private record CheckDef(String id, String name, String category,
                            String description, Supplier<String> action) {
    }

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AuthService authService;
    private final AssetRepository assetRepository;
    private final OsCollectors collectors;
    private final GuardrailService guardrailService;
    private final TerminalPolicy terminalPolicy;
    private final FileService fileService;
    private final AuditService auditService;
    private final AppProperties properties;
    private final JsonFileStore store;
    private final ObjectMapper mapper;

    private final Object lock = new Object();

    public AcceptanceService(AuthService authService, AssetRepository assetRepository,
                             OsCollectors collectors, GuardrailService guardrailService,
                             TerminalPolicy terminalPolicy, FileService fileService,
                             AuditService auditService, AppProperties properties,
                             JsonFileStore store, ObjectMapper mapper) {
        this.authService = authService;
        this.assetRepository = assetRepository;
        this.collectors = collectors;
        this.guardrailService = guardrailService;
        this.terminalPolicy = terminalPolicy;
        this.fileService = fileService;
        this.auditService = auditService;
        this.properties = properties;
        this.store = store;
        this.mapper = mapper;
    }

    public List<Map<String, Object>> catalog() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CheckDef def : defs()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", def.id());
            item.put("name", def.name());
            item.put("category", def.category());
            item.put("description", def.description());
            result.add(item);
        }
        return result;
    }

    public synchronized Map<String, Object> run(List<String> ids, String trigger) {
        List<CheckResult> results = new ArrayList<>();
        Instant startedAt = Instant.now();
        for (CheckDef def : defs()) {
            if (ids != null && !ids.isEmpty() && !ids.contains(def.id())) {
                continue;
            }
            long start = System.currentTimeMillis();
            try {
                String message = def.action().get();
                results.add(new CheckResult(def.id(), def.name(), def.category(),
                        "passed", System.currentTimeMillis() - start, message));
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                results.add(new CheckResult(def.id(), def.name(), def.category(),
                        "failed", System.currentTimeMillis() - start, message));
            }
        }
        long passed = results.stream().filter(r -> "passed".equals(r.status())).count();

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("run_id", "run-" + System.currentTimeMillis());
        record.put("trigger", trigger);
        record.put("started_at", startedAt.toString());
        record.put("finished_at", Instant.now().toString());
        record.put("total", results.size());
        record.put("passed", passed);
        record.put("failed", results.size() - passed);
        record.put("results", results);

        persist(record);
        auditService.append("acceptance", "system", "test-run",
                String.valueOf(record.get("run_id")),
                passed == results.size() ? "success" : "failure",
                Map.of("trigger", trigger, "passed", passed, "total", results.size()));
        return record;
    }

    public List<Map<String, Object>> runs(int limit) {
        List<Path> files = listRunFiles();
        List<Map<String, Object>> summaries = new ArrayList<>();
        int from = Math.max(0, files.size() - limit);
        for (int i = files.size() - 1; i >= from && summaries.size() < limit; i--) {
            Map<String, Object> full = store.read(files.get(i), MAP_TYPE, () -> null);
            if (full == null) {
                continue;
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("run_id", full.get("run_id"));
            summary.put("trigger", full.get("trigger"));
            summary.put("started_at", full.get("started_at"));
            summary.put("total", full.get("total"));
            summary.put("passed", full.get("passed"));
            summary.put("failed", full.get("failed"));
            summaries.add(summary);
        }
        return summaries;
    }

    public Map<String, Object> detail(String runId) {
        Path file = runDir().resolve(requireId(runId) + ".json");
        if (!Files.exists(file)) {
            throw AppException.notFound("run_not_found");
        }
        return store.read(file, MAP_TYPE, () -> null);
    }

    public String report(String runId) {
        Map<String, Object> record = detail(runId);
        StringBuilder sb = new StringBuilder();
        sb.append("# 验收测试报告\n\n");
        sb.append("- 运行 ID：").append(record.get("run_id")).append('\n');
        sb.append("- 触发方式：").append(record.get("trigger")).append('\n');
        sb.append("- 开始时间：").append(record.get("started_at")).append('\n');
        sb.append("- 结束时间：").append(record.get("finished_at")).append('\n');
        sb.append("- 结果：通过 ").append(record.get("passed"))
                .append('/').append(record.get("total")).append("\n\n");
        sb.append("| 用例 ID | 名称 | 分类 | 结果 | 耗时(ms) | 信息 |\n");
        sb.append("| --- | --- | --- | --- | --- | --- |\n");
        if (record.get("results") instanceof List<?> results) {
            for (Object item : results) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                sb.append("| ").append(m.get("id"))
                        .append(" | ").append(m.get("name"))
                        .append(" | ").append(m.get("category"))
                        .append(" | ").append(m.get("status"))
                        .append(" | ").append(m.get("duration_ms"))
                        .append(" | ").append(m.get("message") == null ? "" : m.get("message"))
                        .append(" |\n");
            }
        }
        return sb.toString();
    }

    private void persist(Map<String, Object> record) {
        String runId = String.valueOf(record.get("run_id"));
        store.write(runDir().resolve(runId + ".json"), record);
    }

    private Path runDir() {
        try {
            Path dir = Path.of(properties.testRunDir());
            Files.createDirectories(dir);
            return dir;
        } catch (Exception e) {
            throw AppException.validation("test run dir unavailable");
        }
    }

    private List<Path> listRunFiles() {
        Path dir = runDir();
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            List<Path> files = new ArrayList<>();
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(files::add);
            files.sort(Comparator.comparing(p -> p.getFileName().toString()));
            return files;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw AppException.validation("id is required");
        }
        return id;
    }

    private List<CheckDef> defs() {
        List<CheckDef> defs = new ArrayList<>();
        defs.add(new CheckDef("app.health", "应用健康检查", "platform",
                "应用上下文与审计存储可用", () ->
                "audit events visible: " + auditService.totalEvents()));
        defs.add(new CheckDef("auth.initialized", "管理员已初始化", "security",
                "存在已初始化的管理员账号", () -> {
                    if (!authService.initialized()) {
                        throw new IllegalStateException("admin not initialized");
                    }
                    return "admin initialized";
                }));
        defs.add(new CheckDef("assets.store", "资产存储读写", "data",
                "资产中心文档可正常加载", () -> {
                    int count = assetRepository.load().assets.size();
                    return "assets readable, current count: " + count;
                }));
        defs.add(new CheckDef("os.snapshot", "OS 只读采集", "monitor",
                "能够采集主机名与内存指标", () -> {
                    var snapshot = collectors.readOnlySnapshot();
                    if (snapshot.get("hostname") == null) {
                        throw new IllegalStateException("hostname missing");
                    }
                    return "hostname=" + snapshot.get("hostname");
                }));
        defs.add(new CheckDef("guardrails.engine", "护栏引擎评估", "security",
                "良性命令应被放行", () -> {
                    Map<String, Object> result = guardrailService.evaluate(
                            "acceptance", List.of("df -kP"));
                    if (!"allowed".equals(result.get("overall"))) {
                        throw new IllegalStateException(
                                "expected allowed, got " + result.get("overall"));
                    }
                    return "overall allowed";
                }));
        defs.add(new CheckDef("terminal.policy", "终端 allowlist 策略", "security",
                "白名单命令放行且危险命令拦截", () -> {
                    if (!terminalPolicy.evaluate("hostname").allowed()) {
                        throw new IllegalStateException("allowlisted command rejected");
                    }
                    if (terminalPolicy.evaluate("rm -rf /").allowed()) {
                        throw new IllegalStateException("dangerous command not blocked");
                    }
                    return "policy verdicts correct";
                }));
        defs.add(new CheckDef("files.boundary", "文件边界防护", "security",
                "根外路径访问必须被拒绝", () -> {
                    try {
                        fileService.list("/etc");
                    } catch (AppException expected) {
                        return "boundary rejection works";
                    }
                    throw new IllegalStateException("outside-root path was not rejected");
                }));
        defs.add(new CheckDef("audit.hashchain", "审计 Hash 链完整", "audit",
                "SHA-256 审计链校验通过", () -> {
                    Map<String, Object> verified = auditService.verifyChain();
                    if (!Boolean.TRUE.equals(verified.get("valid"))) {
                        throw new IllegalStateException("audit chain invalid");
                    }
                    return "chain valid, checked " + verified.get("checked");
                }));
        return defs;
    }
}
