package com.kylin.agent.service;

import com.kylin.agent.audit.AuditService;
import com.kylin.agent.domain.ChangeRecord;
import com.kylin.agent.domain.ConfigVersion;
import com.kylin.agent.domain.ServiceEntry;
import com.kylin.agent.repository.ServiceCenterRepository;
import com.kylin.agent.security.AuthInterceptor;
import com.kylin.agent.security.Tokens;
import com.kylin.agent.web.AppException;
import com.kylin.agent.web.TextDiff;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ServiceCenterService {

    private final ServiceCenterRepository repository;
    private final Tokens tokens;
    private final AuditService audit;

    public ServiceCenterService(ServiceCenterRepository repository, Tokens tokens, AuditService audit) {
        this.repository = repository;
        this.tokens = tokens;
        this.audit = audit;
    }

    public List<ServiceEntry> list() {
        return List.copyOf(repository.load().services);
    }

    public List<Map<String, Object>> listMaps() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ServiceEntry entry : repository.load().services) {
            result.add(toMap(entry));
        }
        return result;
    }

    public Map<String, Object> upsert(Map<String, Object> body) {
        String id = str(body.get("id"));
        ServiceCenterRepository.ServiceCenterDocument doc = repository.load();
        ServiceEntry entry;
        if (id != null) {
            entry = findService(doc, id);
        } else {
            String name = str(body.get("name"));
            if (name == null || name.isBlank()) {
                throw AppException.validation("name is required");
            }
            entry = new ServiceEntry();
            entry.id = "svc-" + tokens.shortId();
            entry.name = name;
            entry.createdAt = Instant.now();
            doc.services.add(entry);
        }
        assign(entry, body);
        entry.updatedAt = Instant.now();
        repository.save(doc);
        recordChange(doc, entry.id, id == null ? "service_create" : "service_update",
                Map.of("name", String.valueOf(entry.name)));
        audit.append("service", operator(), id == null ? "create" : "update", "service:" + entry.id,
                "success", Map.of());
        return toMap(entry);
    }

    public Map<String, Object> detail(String id) {
        return toMap(findService(repository.load(), requireId(id)));
    }

    public Map<String, Object> lifecycle(Map<String, Object> body) {
        String serviceId = requireId(str(body.get("service_id")));
        String action = str(body.getOrDefault("action", ""));
        List<String> command = switch (action) {
            case "start" -> List.of("systemctl", "start");
            case "stop" -> List.of("systemctl", "stop");
            case "restart" -> List.of("systemctl", "restart");
            case "enable" -> List.of("systemctl", "enable");
            case "disable" -> List.of("systemctl", "disable");
            default -> throw AppException.validation(
                    "action must be start, stop, restart, enable or disable");
        };
        ServiceEntry service = findService(repository.load(), serviceId);
        if (service.unit == null || service.unit.isBlank()) {
            throw AppException.validation("service has no systemd unit configured");
        }
        List<String> fullCommand = new ArrayList<>(command);
        fullCommand.add(service.unit);

        boolean executed = false;
        String output;
        if (systemctlAvailable()) {
            output = exec(fullCommand);
            executed = true;
        } else {
            output = "simulated: systemctl not available";
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", action);
        detail.put("unit", service.unit);
        detail.put("executed", executed);
        detail.put("command", String.join(" ", fullCommand));
        detail.put("output", truncate(output));
        ServiceCenterRepository.ServiceCenterDocument doc = repository.load();
        recordChange(doc, serviceId, "lifecycle", detail);
        audit.append("service", operator(), "lifecycle:" + action, "service:" + serviceId,
                executed ? "success" : "simulated", Map.of());
        return Map.of("status", "ok", "action", action, "unit", service.unit,
                "executed", executed, "output", truncate(output));
    }

    public Map<String, Object> readConfig(String serviceId) {
        Path path = configPathOf(requireId(serviceId));
        return Map.of("path", path.toString(), "content", readFile(path));
    }

    public List<Map<String, Object>> versions(String serviceId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ConfigVersion version : repository.load().configVersions) {
            if (version.serviceId().equals(serviceId)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", version.id());
                item.put("service_id", version.serviceId());
                item.put("config_path", version.configPath());
                item.put("comment", version.comment());
                item.put("created_by", version.createdBy());
                item.put("created_at", String.valueOf(version.createdAt()));
                item.put("size_bytes",
                        version.content() == null ? 0 : version.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                result.add(item);
            }
        }
        return result;
    }

    public ConfigVersion saveVersion(Map<String, Object> body) {
        String serviceId = requireId(str(body.get("service_id")));
        ServiceEntry service = findService(repository.load(), serviceId);
        Object rawContent = body.get("content");
        if (rawContent == null) {
            throw AppException.validation("content is required");
        }
        ConfigVersion version = new ConfigVersion(
                "cv-" + tokens.shortId(),
                serviceId,
                service.configPath,
                String.valueOf(rawContent),
                str(body.get("comment")),
                operator(),
                Instant.now());
        ServiceCenterRepository.ServiceCenterDocument doc = repository.load();
        doc.configVersions.add(version);
        recordChange(doc, serviceId, "config_save",
                Map.of("version_id", version.id(), "comment", String.valueOf(version.comment())));
        repository.save(doc);
        audit.append("service", operator(), "config-save", "service:" + serviceId, "success",
                Map.of("version_id", version.id()));
        return version;
    }

    public ConfigVersion versionDetail(String id) {
        if (id == null || id.isBlank()) {
            throw AppException.validation("id is required");
        }
        return repository.load().configVersions.stream()
                .filter(v -> v.id().equals(id))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("version_not_found"));
    }

    public Map<String, Object> diff(String fromId, String toId) {
        if (fromId == null || toId == null) {
            throw AppException.validation("from and to are required");
        }
        String fromContent;
        String toContent;
        if ("disk".equals(toId)) {
            ConfigVersion from = versionDetail(fromId);
            fromContent = nullToEmpty(from.content());
            toContent = readFile(Path.of(from.configPath()));
        } else {
            fromContent = nullToEmpty(versionDetail(fromId).content());
            toContent = nullToEmpty(versionDetail(toId).content());
        }
        List<Map<String, Object>> lines =
                TextDiff.diffLines(fromContent.split("\n", -1), toContent.split("\n", -1));
        long added = lines.stream().filter(l -> "add".equals(l.get("op"))).count();
        long removed = lines.stream().filter(l -> "remove".equals(l.get("op"))).count();
        return Map.of("lines", lines, "added", added, "removed", removed);
    }

    public Map<String, Object> drift(String serviceId, String versionId) {
        ServiceEntry service = findService(repository.load(), requireId(serviceId));
        if (service.configPath == null || service.configPath.isBlank()) {
            throw AppException.validation("service has no config_path configured");
        }
        List<ConfigVersion> versions = repository.load().configVersions.stream()
                .filter(v -> v.serviceId().equals(service.id))
                .toList();
        if (versions.isEmpty()) {
            throw AppException.notFound("no saved versions for this service");
        }
        ConfigVersion baseline;
        if (versionId != null && !versionId.isBlank()) {
            baseline = versions.stream()
                    .filter(v -> v.id().equals(versionId))
                    .findFirst()
                    .orElseThrow(() -> AppException.notFound("version_not_found"));
        } else {
            baseline = versions.get(versions.size() - 1);
        }
        String disk = readFile(Path.of(service.configPath));
        boolean drifted = !nullToEmpty(baseline.content()).equals(nullToEmpty(disk));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service_id", serviceId);
        result.put("baseline_version_id", baseline.id());
        result.put("drifted", drifted);
        if (drifted) {
            List<Map<String, Object>> lines =
                    TextDiff.diffLines(nullToEmpty(baseline.content()).split("\n", -1), disk.split("\n", -1));
            result.put("diff", lines);
        }
        return result;
    }

    public Map<String, Object> rollback(Map<String, Object> body) {
        String serviceId = requireId(str(body.get("service_id")));
        String versionId = requireId(str(body.get("version_id")));
        ServiceEntry service = findService(repository.load(), serviceId);
        ConfigVersion target = versionDetail(versionId);
        if (!target.serviceId().equals(serviceId)) {
            throw AppException.validation("version does not belong to this service");
        }
        if (service.configPath == null || service.configPath.isBlank()) {
            throw AppException.validation("service has no config_path configured");
        }
        writeFile(Path.of(service.configPath), nullToEmpty(target.content()));
        ServiceCenterRepository.ServiceCenterDocument doc = repository.load();
        recordChange(doc, serviceId, "config_rollback", Map.of("version_id", versionId));
        repository.save(doc);
        audit.append("service", operator(), "config-rollback", "service:" + serviceId, "success",
                Map.of("version_id", versionId));
        return Map.of("status", "ok", "rolled_back_to", versionId);
    }

    public List<ChangeRecord> changes(String serviceId, int limit) {
        List<ChangeRecord> changes = repository.load().changes;
        List<ChangeRecord> filtered = serviceId == null
                ? changes
                : changes.stream().filter(c -> serviceId.equals(c.serviceId())).toList();
        int from = Math.max(0, filtered.size() - limit);
        List<ChangeRecord> result = new ArrayList<>();
        for (int i = filtered.size() - 1; i >= from; i--) {
            result.add(filtered.get(i));
        }
        return result;
    }

    private void recordChange(ServiceCenterRepository.ServiceCenterDocument doc,
                              String serviceId, String type, Map<String, Object> detail) {
        ChangeRecord change = new ChangeRecord("chg-" + tokens.shortId(), serviceId,
                Instant.now(), type, detail, operator());
        doc.changes.add(change);
        repository.save(doc);
    }

    private static boolean systemctlAvailable() {
        try {
            Process process = new ProcessBuilder("systemctl", "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            process.destroyForcibly();
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String exec(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "timeout";
            }
            return new String(process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "exec failed: " + e.getMessage();
        }
    }

    private Path configPathOf(String serviceId) {
        ServiceEntry service = findService(repository.load(), serviceId);
        if (service.configPath == null || service.configPath.isBlank()) {
            throw AppException.validation("service has no config_path configured");
        }
        return Path.of(service.configPath);
    }

    private String readFile(Path path) {
        if (!Files.exists(path)) {
            throw AppException.notFound("config file not found: " + path);
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw AppException.validation("failed to read config file");
        }
    }

    private void writeFile(Path path, String content) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content);
        } catch (IOException e) {
            throw AppException.validation("failed to write config file");
        }
    }

    private ServiceEntry findService(ServiceCenterRepository.ServiceCenterDocument doc, String idOrName) {
        return doc.services.stream()
                .filter(s -> s.id.equals(idOrName) || s.name.equals(idOrName))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("service_not_found"));
    }

    private static void assign(ServiceEntry entry, Map<String, Object> body) {
        if (body.containsKey("name") && str(body.get("name")) != null) {
            entry.name = str(body.get("name"));
        }
        if (body.containsKey("asset_id")) {
            entry.assetId = str(body.get("asset_id"));
        }
        if (body.containsKey("unit")) {
            entry.unit = str(body.get("unit"));
        }
        if (body.containsKey("config_path")) {
            entry.configPath = str(body.get("config_path"));
        }
        if (body.containsKey("description")) {
            entry.description = str(body.get("description"));
        }
        if (body.containsKey("desired_state")) {
            entry.desiredState = str(body.get("desired_state"));
        }
    }

    private static Map<String, Object> toMap(ServiceEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.id);
        map.put("name", entry.name);
        map.put("asset_id", entry.assetId);
        map.put("unit", entry.unit);
        map.put("config_path", entry.configPath);
        map.put("description", entry.description);
        map.put("desired_state", entry.desiredState);
        map.put("created_at", entry.createdAt == null ? null : entry.createdAt.toString());
        map.put("updated_at", entry.updatedAt == null ? null : entry.updatedAt.toString());
        return map;
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw AppException.validation("id is required");
        }
        return id;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
