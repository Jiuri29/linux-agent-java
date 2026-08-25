package com.linux.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linux.agent.audit.AuditService;
import com.linux.agent.domain.McpPlugin;
import com.linux.agent.domain.McpTool;
import com.linux.agent.monitor.OsCollectors;
import com.linux.agent.repository.McpRepository;
import com.linux.agent.security.AuthInterceptor;
import com.linux.agent.security.Tokens;
import com.linux.agent.web.AppException;
import com.linux.agent.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpService {

    private static final List<String> EXECUTABLE = List.of(
            "os.read_only_snapshot", "monitor.latest", "assets.list", "services.list",
            "files.list", "guardrails.evaluate", "audit.recent");

    private final McpRepository repository;
    private final OsCollectors collectors;
    private final MonitoringService monitoringService;
    private final AssetService assetService;
    private final ServiceCenterService serviceCenterService;
    private final FileService fileService;
    private final GuardrailService guardrailService;
    private final AuditService auditService;
    private final ObjectMapper mapper;
    private final Tokens tokens;
    private final AuditService audit;

    public McpService(McpRepository repository, OsCollectors collectors,
                      MonitoringService monitoringService, AssetService assetService,
                      ServiceCenterService serviceCenterService, FileService fileService,
                      GuardrailService guardrailService, AuditService auditService,
                      ObjectMapper mapper, Tokens tokens, AuditService audit) {
        this.repository = repository;
        this.collectors = collectors;
        this.monitoringService = monitoringService;
        this.assetService = assetService;
        this.serviceCenterService = serviceCenterService;
        this.fileService = fileService;
        this.guardrailService = guardrailService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.tokens = tokens;
        this.audit = audit;
    }

    public synchronized List<McpTool> tools() {
        return List.copyOf(loadWithBuiltins().tools);
    }

    public Map<String, Object> capabilities() {
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "Linux-aiops-agent");
        serverInfo.put("version", "1.0.0");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocol_version", "2024-11-05");
        result.put("server_info", serverInfo);
        result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        result.put("tools_count", loadWithBuiltins().tools.size());
        return result;
    }

    public synchronized Map<String, Object> invoke(String name, Map<String, Object> params) {
        McpRepository.McpDocument doc = loadWithBuiltins();
        McpTool tool = doc.tools.stream()
                .filter(t -> t.name.equals(name))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("tool_not_found"));
        if (!tool.enabled) {
            throw AppException.validation("tool is disabled: " + name);
        }

        long start = System.currentTimeMillis();
        if (!EXECUTABLE.contains(name)) {
            logInvocation(name, params, "not_executable", start);
            return Map.of("status", "not_executable",
                    "note", "plugin-provided tools are metadata-only in this simplified edition");
        }
        try {
            Map<String, Object> result = dispatch(name, params == null ? Map.of() : params);
            logInvocation(name, params, "success", start);
            return result;
        } catch (AppException e) {
            logInvocation(name, params, e.code().value(), start);
            throw e;
        }
    }

    public synchronized Object rpc(JsonNode request) {
        String method = request.path("method").asText("");
        JsonNode id = request.path("id");
        try {
            Object result = switch (method) {
                case "initialize" -> capabilities();
                case "tools/list" -> tools();
                case "tools/call" -> {
                    JsonNode params = request.path("params");
                    yield invoke(params.path("name").asText(),
                            jsonNodeToMap(params.path("arguments")));
                }
                default -> throw new AppException(ErrorCode.NOT_FOUND, "method not found: " + method);
            };
            return successResponse(id, result);
        } catch (AppException e) {
            int code = -32603;
            if (e.code() == ErrorCode.NOT_FOUND) {
                code = -32601;
            } else if (e.code() == ErrorCode.VALIDATION_ERROR) {
                code = -32602;
            }
            return errorResponse(id, code, e.getMessage());
        }
    }

    public synchronized List<McpPlugin> plugins() {
        return List.copyOf(loadWithBuiltins().plugins);
    }

    public synchronized McpPlugin registerPlugin(Map<String, Object> body) {
        String pluginName = str(body.get("name"));
        if (pluginName == null || pluginName.isBlank()) {
            throw AppException.validation("name is required");
        }
        McpRepository.McpDocument doc = loadWithBuiltins();
        if (doc.plugins.stream().anyMatch(p -> p.name.equals(pluginName))) {
            throw AppException.validation("plugin already registered");
        }
        McpPlugin plugin = new McpPlugin();
        plugin.id = "plg-" + tokens.shortId();
        plugin.name = pluginName;
        plugin.description = str(body.get("description"));
        plugin.registeredAt = Instant.now();

        if (body.get("tools") instanceof List<?> rawTools) {
            for (Object item : rawTools) {
                if (!(item instanceof Map<?, ?> toolMap)) {
                    continue;
                }
                String toolName = str(toolMap.get("name"));
                if (toolName == null || toolName.isBlank()) {
                    continue;
                }
                if (doc.tools.stream().anyMatch(t -> t.name.equals(toolName))) {
                    continue;
                }
                McpTool tool = new McpTool();
                tool.name = toolName;
                tool.description = str(toolMap.get("description"));
                tool.builtin = false;
                tool.pluginId = plugin.id;
                plugin.providedTools.add(tool);
                doc.tools.add(tool);
            }
        }
        doc.plugins.add(plugin);
        repository.save(doc);
        audit.append("mcp", operator(), "plugin-register", pluginName, "success",
                Map.of("id", plugin.id));
        return plugin;
    }

    public synchronized Map<String, Object> setPluginStatus(Map<String, Object> body) {
        McpRepository.McpDocument doc = loadWithBuiltins();
        McpPlugin plugin = doc.plugins.stream()
                .filter(p -> p.id.equals(str(body.get("id"))) || p.name.equals(str(body.get("name"))))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("plugin_not_found"));
        plugin.enabled = parseEnabled(body);
        doc.tools.forEach(t -> {
            if (plugin.id.equals(t.pluginId)) {
                t.enabled = plugin.enabled;
            }
        });
        repository.save(doc);
        audit.append("mcp", operator(), plugin.enabled ? "plugin-enable" : "plugin-disable",
                plugin.name, "success", Map.of());
        return Map.of("status", "ok", "id", plugin.id, "enabled", plugin.enabled);
    }

    public synchronized Map<String, Object> uninstallPlugin(Map<String, Object> body) {
        McpRepository.McpDocument doc = loadWithBuiltins();
        McpPlugin plugin = doc.plugins.stream()
                .filter(p -> p.id.equals(str(body.get("id"))) || p.name.equals(str(body.get("name"))))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("plugin_not_found"));
        if ("core".equals(plugin.name)) {
            throw AppException.validation("core plugin cannot be uninstalled");
        }
        doc.plugins.remove(plugin);
        doc.tools.removeIf(t -> plugin.id.equals(t.pluginId));
        repository.save(doc);
        audit.append("mcp", operator(), "plugin-uninstall", plugin.name, "success", Map.of());
        return Map.of("status", "ok", "uninstalled", plugin.name);
    }

    public synchronized Map<String, Object> setToolStatus(Map<String, Object> body) {
        McpRepository.McpDocument doc = loadWithBuiltins();
        McpTool tool = doc.tools.stream()
                .filter(t -> t.name.equals(str(body.get("name"))))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("tool_not_found"));
        tool.enabled = parseEnabled(body);
        repository.save(doc);
        audit.append("mcp", operator(), tool.enabled ? "tool-enable" : "tool-disable",
                tool.name, "success", Map.of());
        return Map.of("status", "ok", "name", tool.name, "enabled", tool.enabled);
    }

    private Map<String, Object> dispatch(String name, Map<String, Object> params) {
        return switch (name) {
            case "os.read_only_snapshot" -> collectors.readOnlySnapshot();
            case "monitor.latest" -> {
                Map<String, Object> latest = monitoringService.latest();
                yield latest == null ? Map.of() : latest;
            }
            case "assets.list" -> Map.of("items", assetService.listAllMaps());
            case "services.list" -> Map.of("items", serviceCenterService.listMaps());
            case "files.list" -> Map.of("entries",
                    fileService.list(str(params.getOrDefault("path", ""))));
            case "guardrails.evaluate" -> {
                @SuppressWarnings("unchecked")
                List<String> commands = params.get("commands") instanceof List
                        ? (List<String>) params.get("commands") : List.of();
                yield guardrailService.evaluate(
                        str(params.getOrDefault("intent", "mcp invoke")), commands);
            }
            case "audit.recent" -> Map.of("events",
                    auditService.recent(intValue(params.getOrDefault("limit", 20))));
            default -> throw AppException.notFound("tool_not_executable");
        };
    }

    private void logInvocation(String tool, Map<String, Object> params, String status, long startMillis) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("operator", operator());
        entry.put("tool", tool);
        entry.put("params", params);
        entry.put("status", status);
        entry.put("duration_ms", System.currentTimeMillis() - startMillis);
        repository.appendInvocation(entry);
    }

    private McpRepository.McpDocument loadWithBuiltins() {
        McpRepository.McpDocument doc = repository.load();
        if (doc.tools.isEmpty()) {
            seedDefaults(doc);
        }
        return doc;
    }

    private void seedDefaults(McpRepository.McpDocument doc) {
        McpPlugin core = new McpPlugin();
        core.id = "plg-core";
        core.name = "core";
        core.description = "内置只读工具集";
        core.enabled = true;
        core.registeredAt = Instant.now();

        doc.tools.add(builtinTool(core, "os.read_only_snapshot", "只读 OS 快照采集"));
        doc.tools.add(builtinTool(core, "monitor.latest", "最近一次监控快照"));
        doc.tools.add(builtinTool(core, "assets.list", "资产列表"));
        doc.tools.add(builtinTool(core, "services.list", "服务目录列表"));
        McpTool files = builtinTool(core, "files.list", "允许根内目录列表");
        files.params.add(new McpTool.ParamDef("path", "string", false));
        doc.tools.add(files);
        McpTool guardrail = builtinTool(core, "guardrails.evaluate", "护栏评估");
        guardrail.params.add(new McpTool.ParamDef("intent", "string", false));
        guardrail.params.add(new McpTool.ParamDef("commands", "array", true));
        doc.tools.add(guardrail);
        McpTool recent = builtinTool(core, "audit.recent", "最近审计事件");
        recent.params.add(new McpTool.ParamDef("limit", "integer", false));
        doc.tools.add(recent);

        doc.plugins.add(core);
        repository.save(doc);
    }

    private static McpTool builtinTool(McpPlugin plugin, String name, String description) {
        McpTool tool = new McpTool();
        tool.name = name;
        tool.description = description;
        tool.builtin = true;
        tool.pluginId = plugin.id;
        return tool;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return new ObjectMapper().convertValue(node, Map.class);
    }

    private static Map<String, Object> successResponse(JsonNode id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        if (id != null && !id.isMissingNode()) {
            response.put("id", id);
        }
        response.put("result", result);
        return response;
    }

    private static Map<String, Object> errorResponse(JsonNode id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        if (id != null && !id.isMissingNode()) {
            response.put("id", id);
        }
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    private static boolean parseEnabled(Map<String, Object> body) {
        Object enabled = body.get("enabled");
        return enabled instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(enabled));
    }

    private static int intValue(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 20;
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String operator() {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            Object operator = servlet.getRequest().getAttribute(AuthInterceptor.OPERATOR_ATTRIBUTE);
            if (operator != null) {
                return operator.toString();
            }
        }
        return "system";
    }
}
