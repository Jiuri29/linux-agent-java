package com.kylin.agent.controller;

import com.kylin.agent.audit.AuditService;
import com.kylin.agent.repository.AssetRepository;
import com.kylin.agent.repository.MonitorRepository;
import com.kylin.agent.repository.UserRepository;
import com.kylin.agent.security.SessionStore;
import com.kylin.agent.service.AgentConfigService;
import com.kylin.agent.service.AuthService;
import com.kylin.agent.service.McpService;
import com.kylin.agent.service.MetricsService;
import com.kylin.agent.web.AppException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PlatformController {

    private final AuthService authService;
    private final AgentConfigService agentConfigService;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final MonitorRepository monitorRepository;
    private final SessionStore sessionStore;
    private final McpService mcpService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final com.kylin.agent.config.AppProperties properties;

    public PlatformController(AuthService authService, AgentConfigService agentConfigService,
                              UserRepository userRepository, AssetRepository assetRepository,
                              MonitorRepository monitorRepository, SessionStore sessionStore,
                              McpService mcpService, AuditService auditService,
                              MetricsService metricsService,
                              com.kylin.agent.config.AppProperties properties) {
        this.authService = authService;
        this.agentConfigService = agentConfigService;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.monitorRepository = monitorRepository;
        this.sessionStore = sessionStore;
        this.mcpService = mcpService;
        this.auditService = auditService;
        this.metricsService = metricsService;
        this.properties = properties;
    }

    @GetMapping({"/api/runtime/context", "/api/v1/runtime/context"})
    public Map<String, Object> runtimeContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("version", "1.0.0");
        context.put("hostname", hostname());
        context.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        context.put("arch", System.getProperty("os.arch"));
        context.put("java_version", System.getProperty("java.version"));
        context.put("uptime_seconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        context.put("data_dir", properties.dataDir());
        context.put("frontend_dir", properties.frontendDir());
        context.put("admin_initialized", authService.initialized());
        var agentConfig = agentConfigService.get();
        context.put("agent_api_configured",
                Boolean.TRUE.equals(agentConfig.get("configured"))
                        && Boolean.TRUE.equals(agentConfig.get("enabled")));
        context.put("generated_at", Instant.now().toString());
        return context;
    }

    @GetMapping("/api/admin/summary")
    public Map<String, Object> adminSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("users_total", userRepository.loadAll().size());
        summary.put("assets_total", assetRepository.load().assets.size());
        summary.put("services_total", 0);
        summary.put("active_alerts", activeAlerts());
        summary.put("sessions_active", sessionStore.size());
        summary.put("mcp_tools_total", mcpService.tools().size());
        summary.put("audit_events_total", auditService.totalEvents());
        summary.put("admin_initialized", authService.initialized());
        return summary;
    }

    @GetMapping({"/api/admin/health", "/api/v1/admin/health"})
    public Map<String, Object> adminHealth() {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(dirCheck("data_dir_writable", Path.of(properties.dataDir()), true));
        checks.add(fileCheck("auth_store_present", Path.of(properties.authStore()), true));
        checks.add(dirCheck("audit_dir_writable", Path.of(properties.auditLog()).getParent(), true));
        Map<String, Object> frontend = new LinkedHashMap<>(Map.of(
                "name", "frontend_dir",
                "ok", Files.isDirectory(Path.of(properties.frontendDir())),
                "critical", false,
                "detail", "optional; static console hosting"));
        checks.add(frontend);
        boolean overall = checks.stream()
                .filter(c -> Boolean.TRUE.equals(c.get("critical")))
                .allMatch(c -> Boolean.TRUE.equals(c.get("ok")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overall", overall ? "ok" : "degraded");
        result.put("checks", checks);
        result.put("checked_at", Instant.now().toString());
        return result;
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> metrics(HttpServletRequest request) {
        if (!isLoopback(request) && !properties.metricsPublic()) {
            throw AppException.permissionDenied("metrics:read");
        }
        long assets = assetRepository.load().assets.size();
        long users = userRepository.loadAll().size();
        long alerts = activeAlerts();
        long auditEvents = auditService.totalEvents();
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/plain; version=0.0.4"))
                .body(metricsService.render(assets, users, alerts, auditEvents));
    }

    private long activeAlerts() {
        return monitorRepository.loadState().alerts.stream()
                .filter(a -> List.of("active", "acknowledged", "escalated").contains(a.status))
                .count();
    }

    private static boolean isLoopback(HttpServletRequest request) {
        String addr = request.getRemoteAddr();
        if (addr == null) {
            return false;
        }
        if (addr.startsWith("127.") || addr.equals("::1") || addr.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        try {
            return InetAddress.getByName(addr).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private static Map<String, Object> dirCheck(String name, Path dir, boolean critical) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("name", name);
        check.put("critical", critical);
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path probe = Files.createTempFile(dir, "probe", ".tmp");
            Files.delete(probe);
            check.put("ok", true);
            check.put("detail", dir.toString());
        } catch (Exception e) {
            check.put("ok", false);
            check.put("detail", String.valueOf(e.getMessage()));
        }
        return check;
    }

    private static Map<String, Object> fileCheck(String name, Path file, boolean critical) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("name", name);
        check.put("critical", critical);
        check.put("ok", Files.exists(file));
        check.put("detail", file.toString());
        return check;
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
