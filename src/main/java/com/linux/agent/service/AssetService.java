package com.linux.agent.service;

import com.linux.agent.audit.AuditService;
import com.linux.agent.domain.Asset;
import com.linux.agent.domain.AssetDependency;
import com.linux.agent.domain.AssetPort;
import com.linux.agent.domain.AssetSnapshot;
import com.linux.agent.monitor.OsCollectors;
import com.linux.agent.repository.AssetRepository;
import com.linux.agent.security.AuthInterceptor;
import com.linux.agent.security.Tokens;
import com.linux.agent.web.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AssetService {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private final AssetRepository repository;
    private final OsCollectors collectors;
    private final Tokens tokens;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final com.linux.agent.config.AppProperties properties;

    public AssetService(AssetRepository repository, OsCollectors collectors, Tokens tokens,
                        AuditService audit, ObjectMapper mapper,
                        com.linux.agent.config.AppProperties properties) {
        this.repository = repository;
        this.collectors = collectors;
        this.tokens = tokens;
        this.audit = audit;
        this.mapper = mapper;
        this.properties = properties;
    }

    public List<Map<String, Object>> listAllMaps() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Asset asset : repository.load().assets) {
            result.add(toMap(asset));
        }
        return result;
    }

    public Map<String, Object> list(jakarta.servlet.http.HttpServletRequest request) {
        int page = intParam(request, "page", 1, 1);
        int pageSize = intParam(request, "page_size", 20, 1);
        String keyword = blankToNull(request.getParameter("keyword"));
        String status = blankToNull(request.getParameter("status"));
        String environment = blankToNull(request.getParameter("environment"));
        String tag = blankToNull(request.getParameter("tag"));
        String group = blankToNull(request.getParameter("group"));
        String application = blankToNull(request.getParameter("application"));

        List<Map<String, Object>> matched = new ArrayList<>();
        for (Asset asset : repository.load().assets) {
            if (keyword != null
                    && !contains(asset.hostname, keyword) && !contains(asset.ip, keyword)) {
                continue;
            }
            if (status != null && !equalsIgnoreCase(status, asset.status)) {
                continue;
            }
            if (environment != null && !equalsIgnoreCase(environment, asset.environment)) {
                continue;
            }
            if (tag != null && !asset.tags.contains(tag)) {
                continue;
            }
            if (group != null && !asset.groups.contains(group)) {
                continue;
            }
            if (application != null && !asset.applications.contains(application)) {
                continue;
            }
            matched.add(toMap(asset));
        }
        int fromIndex = Math.min((page - 1) * pageSize, matched.size());
        int toIndex = Math.min(fromIndex + pageSize, matched.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", matched.subList(fromIndex, toIndex));
        result.put("total", matched.size());
        result.put("page", page);
        result.put("page_size", pageSize);
        return result;
    }

    public Map<String, Object> detail(String id) {
        return toMap(requireAsset(id));
    }

    public Map<String, Object> create(Map<String, Object> body) {
        String hostname = str(body.get("hostname"));
        if (hostname == null || hostname.isBlank()) {
            throw AppException.validation("hostname is required");
        }
        String ip = str(body.get("ip"));
        validateIp(ip);

        AssetRepository.AssetDocument doc = repository.load();
        if (doc.assets.stream().anyMatch(a -> ip.equals(a.ip))) {
            throw AppException.validation("duplicate asset ip: " + ip);
        }
        Asset asset = new Asset();
        applyFields(asset, body, true);
        asset.id = "ast-" + tokens.shortId();
        asset.createdAt = Instant.now();
        asset.updatedAt = asset.createdAt;
        doc.assets.add(asset);
        repository.save(doc);
        audit.append("asset", operator(), "create", "asset:" + asset.id, "success",
                Map.of("hostname", hostname, "ip", ip));
        return toMap(asset);
    }

    public Map<String, Object> update(Map<String, Object> body) {
        String id = str(body.containsKey("id") ? body.get("id") : body.get("asset_id"));
        Asset asset = requireAsset(id);
        AssetRepository.AssetDocument doc = repository.load();
        String newIp = str(body.get("ip"));
        if (newIp != null && !newIp.equals(asset.ip)) {
            validateIp(newIp);
            if (doc.assets.stream().anyMatch(a -> newIp.equals(a.ip))) {
                throw AppException.validation("duplicate asset ip: " + newIp);
            }
        }
        applyFields(asset, body, false);
        asset.updatedAt = Instant.now();
        repository.save(doc);
        audit.append("asset", operator(), "update", "asset:" + asset.id, "success", Map.of());
        return toMap(asset);
    }

    public void delete(String id) {
        AssetRepository.AssetDocument doc = repository.load();
        Asset asset = doc.assets.stream()
                .filter(a -> a.id.equals(id))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("asset_not_found"));
        doc.assets.remove(asset);
        doc.dependencies.removeIf(d -> id.equals(d.sourceAssetId()) || id.equals(d.targetAssetId()));
        repository.save(doc);
        audit.append("asset", operator(), "delete", "asset:" + id, "success",
                Map.of("hostname", String.valueOf(asset.hostname)));
    }

    public List<String> groups() {
        return List.copyOf(repository.load().groups);
    }

    public Map<String, Object> addGroup(String name) {
        requireName(name);
        AssetRepository.AssetDocument doc = repository.load();
        if (!doc.groups.contains(name)) {
            doc.groups.add(name);
            repository.save(doc);
        }
        return Map.of("status", "ok", "name", name);
    }

    public void deleteGroup(String name) {
        AssetRepository.AssetDocument doc = repository.load();
        doc.groups.remove(name);
        doc.assets.forEach(a -> a.groups.remove(name));
        repository.save(doc);
        audit.append("asset", operator(), "delete-group", "group:" + name, "success", Map.of());
    }

    public List<String> tags() {
        return List.copyOf(repository.load().tags);
    }

    public Map<String, Object> addTag(String name) {
        requireName(name);
        AssetRepository.AssetDocument doc = repository.load();
        if (!doc.tags.contains(name)) {
            doc.tags.add(name);
            repository.save(doc);
        }
        return Map.of("status", "ok", "name", name);
    }

    public void deleteTag(String name) {
        AssetRepository.AssetDocument doc = repository.load();
        doc.tags.remove(name);
        doc.assets.forEach(a -> a.tags.remove(name));
        repository.save(doc);
        audit.append("asset", operator(), "delete-tag", "tag:" + name, "success", Map.of());
    }

    public List<String> applications() {
        return List.copyOf(repository.load().applications);
    }

    public Map<String, Object> addApplication(String name) {
        requireName(name);
        AssetRepository.AssetDocument doc = repository.load();
        if (!doc.applications.contains(name)) {
            doc.applications.add(name);
            repository.save(doc);
        }
        return Map.of("status", "ok", "name", name);
    }

    public Map<String, Object> addPorts(Map<String, Object> body) {
        Asset asset = requireAsset(str(body.getOrDefault("asset_id", body.get("id"))));
        Object rawPorts = body.get("ports");
        if (!(rawPorts instanceof List<?> portList) || portList.isEmpty()) {
            throw AppException.validation("ports is required");
        }
        ObjectMapper m = mapper;
        List<AssetPort> incoming = new ArrayList<>();
        for (Object item : portList) {
            AssetPort port = m.convertValue(item, AssetPort.class);
            if (port.port() <= 0 || port.port() > 65535) {
                throw AppException.validation("port out of range: " + port.port());
            }
            incoming.add(port);
        }
        AssetRepository.AssetDocument doc = repository.load();
        Asset target = requireIn(doc.assets, asset.id);
        for (AssetPort port : incoming) {
            target.ports.removeIf(p -> p.port() == port.port());
            target.ports.add(port);
        }
        target.updatedAt = Instant.now();
        repository.save(doc);
        return toMap(target);
    }

    public Map<String, Object> addDependency(Map<String, Object> body) {
        String sourceId = str(body.get("source_asset_id"));
        String targetId = str(body.get("target_asset_id"));
        if (sourceId == null || targetId == null) {
            throw AppException.validation("source_asset_id and target_asset_id are required");
        }
        AssetRepository.AssetDocument doc = repository.load();
        if (doc.assets.stream().noneMatch(a -> a.id.equals(sourceId))
                || doc.assets.stream().noneMatch(a -> a.id.equals(targetId))) {
            throw AppException.notFound("asset_not_found");
        }
        String type = str(body.getOrDefault("type", "depends_on"));
        doc.dependencies.removeIf(d -> sourceId.equals(d.sourceAssetId()) && targetId.equals(d.targetAssetId()));
        doc.dependencies.add(new AssetDependency(sourceId, targetId, type));
        repository.save(doc);
        return Map.of("status", "ok");
    }

    public List<AssetDependency> dependencies() {
        return List.copyOf(repository.load().dependencies);
    }

    public List<AssetSnapshot> snapshots(String assetId, String hostId) {
        return repository.load().snapshots.stream()
                .filter(s -> assetId == null || assetId.equals(s.assetId()))
                .filter(s -> hostId == null || hostId.equals(s.hostId()))
                .toList();
    }

    public Map<String, Object> sync(String source) {
        switch (source == null ? "" : source.toLowerCase(Locale.ROOT)) {
            case "manual" -> {
                return Map.of("status", "ok", "synced", 0);
            }
            case "local" -> {
                Map<String, Object> snapshot = collectors.readOnlySnapshot();
                String hostname = String.valueOf(snapshot.get("hostname"));
                Asset local = findOrCreateLocal(hostname);
                storeSnapshot(local.id, snapshot);
                audit.append("asset", operator(), "sync", "asset:" + local.id, "success",
                        Map.of("source", "local"));
                return Map.of("status", "ok", "synced", 1, "asset_id", local.id);
            }
            case "config" -> {
                Path configPath = Path.of(properties.apiConfig());
                int synced = 0;
                if (java.nio.file.Files.exists(configPath)) {
                    try {
                        var node = mapper.readTree(configPath.toFile());
                        if (node.has("assets") && node.get("assets").isArray()) {
                            for (var entry : node.get("assets")) {
                                Map<String, Object> fields = mapper.convertValue(entry,
                                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                                        });
                                create(fields);
                                synced++;
                            }
                        }
                    } catch (java.io.IOException e) {
                        throw AppException.validation("invalid agent api config file");
                    }
                }
                audit.append("asset", operator(), "sync", "assets", "success",
                        Map.of("source", "config", "synced", synced));
                return Map.of("status", "ok", "synced", synced);
            }
            default -> throw AppException.validation("source must be manual, local or config");
        }
    }

    private Asset findOrCreateLocal(String hostname) {
        AssetRepository.AssetDocument doc = repository.load();
        Asset existing = doc.assets.stream()
                .filter(a -> hostname.equals(a.hostname))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        Asset asset = new Asset();
        asset.id = "ast-" + tokens.shortId();
        asset.hostname = hostname;
        asset.ip = loopbackIp();
        asset.os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        asset.arch = System.getProperty("os.arch");
        asset.environment = "local";
        asset.status = "online";
        asset.createdAt = Instant.now();
        asset.updatedAt = asset.createdAt;
        doc.assets.add(asset);
        repository.save(doc);
        return asset;
    }

    private void storeSnapshot(String assetId, Map<String, Object> snapshot) {
        AssetRepository.AssetDocument doc = repository.load();
        Asset found = requireIn(doc.assets, assetId);
        doc.snapshots.add(new AssetSnapshot(found.id, found.hostname, Instant.now(), snapshot));
        repository.save(doc);
    }

    private Asset requireAsset(String id) {
        if (id == null || id.isBlank()) {
            throw AppException.validation("id is required");
        }
        AssetRepository.AssetDocument doc = repository.load();
        return doc.assets.stream()
                .filter(a -> a.id.equals(id) || a.hostname.equals(id))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("asset_not_found"));
    }

    private Asset requireIn(List<Asset> assets, String id) {
        return assets.stream()
                .filter(a -> a.id.equals(id))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("asset_not_found"));
    }

    private void applyFields(Asset asset, Map<String, Object> body, boolean creating) {
        ObjectMapper m = mapper;
        String hostname = str(body.get("hostname"));
        if (hostname != null) {
            asset.hostname = hostname;
        } else if (creating) {
            throw AppException.validation("hostname is required");
        }
        String ip = str(body.get("ip"));
        if (ip != null) {
            asset.ip = ip;
        } else if (creating) {
            throw AppException.validation("ip is required");
        }
        assignIfPresent(body, "os", v -> asset.os = str(v));
        assignIfPresent(body, "arch", v -> asset.arch = str(v));
        assignIfPresent(body, "environment", v -> asset.environment = str(v));
        assignIfPresent(body, "status", v -> asset.status = str(v));
        assignListIfPresent(body, "tags", asset.tags);
        assignListIfPresent(body, "groups", asset.groups);
        assignListIfPresent(body, "applications", asset.applications);
        if (body.containsKey("ports") && body.get("ports") instanceof List<?> portList) {
            List<AssetPort> ports = new ArrayList<>();
            for (Object item : portList) {
                ports.add(m.convertValue(item, AssetPort.class));
            }
            asset.ports = ports;
        }
    }

    @FunctionalInterface
    private interface Assigner {
        void accept(Object value);
    }

    private void assignIfPresent(Map<String, Object> body, String key, Assigner assigner) {
        if (body.containsKey(key) && body.get(key) != null) {
            assigner.accept(body.get(key));
        }
    }

    private void assignListIfPresent(Map<String, Object> body, String key, List<String> target) {
        if (body.containsKey(key) && body.get(key) instanceof List<?> values) {
            target.clear();
            for (Object value : values) {
                if (value != null && !String.valueOf(value).isBlank()) {
                    target.add(String.valueOf(value));
                }
            }
        }
    }

    public Map<String, Object> toMap(Asset asset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", asset.id);
        map.put("hostname", asset.hostname);
        map.put("ip", asset.ip);
        map.put("os", asset.os);
        map.put("arch", asset.arch);
        map.put("environment", asset.environment);
        map.put("status", asset.status);
        map.put("tags", asset.tags);
        map.put("groups", asset.groups);
        map.put("applications", asset.applications);
        List<Map<String, Object>> ports = new ArrayList<>();
        for (AssetPort port : asset.ports) {
            ports.add(Map.of("port", port.port(), "service_name",
                    port.serviceName() == null ? "" : port.serviceName()));
        }
        map.put("ports", ports);
        map.put("created_at", asset.createdAt == null ? null : asset.createdAt.toString());
        map.put("updated_at", asset.updatedAt == null ? null : asset.updatedAt.toString());
        return map;
    }

    private void validateIp(String ip) {
        if (ip == null) {
            throw new AppException(com.linux.agent.web.ErrorCode.VALIDATION_ERROR, "ip is required",
                    Map.of("error", "invalid_ip"));
        }
        var matcher = IPV4.matcher(ip.trim());
        boolean valid = matcher.matches();
        if (valid) {
            for (int i = 1; i <= 4; i++) {
                if (Integer.parseInt(matcher.group(i)) > 255) {
                    valid = false;
                    break;
                }
            }
        }
        if (!valid) {
            throw new AppException(com.linux.agent.web.ErrorCode.VALIDATION_ERROR, "invalid ip: " + ip,
                    Map.of("error", "invalid_ip"));
        }
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw AppException.validation("name is required");
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean contains(String field, String keyword) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left.equalsIgnoreCase(right);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int intParam(jakarta.servlet.http.HttpServletRequest request, String name,
                                int defaultValue, int min) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(min, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            throw AppException.validation(name + " must be an integer");
        }
    }

    private String loopbackIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (java.net.UnknownHostException e) {
            return "127.0.0.1";
        }
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
