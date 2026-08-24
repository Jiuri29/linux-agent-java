package com.kylin.agent.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kylin.agent.audit.AuditService;
import com.kylin.agent.config.AppProperties;
import com.kylin.agent.repository.JsonFileStore;
import com.kylin.agent.security.AuthInterceptor;
import com.kylin.agent.security.Tokens;
import com.kylin.agent.web.AppException;
import com.kylin.agent.web.ErrorCode;
import com.kylin.agent.web.TextDiff;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class FileService {

    public record BackupRecord(
            @JsonProperty("backup_id") String backupId,
            @JsonProperty("original_path") String originalPath,
            @JsonProperty("file") String file,
            @JsonProperty("ts") Instant ts,
            @JsonProperty("size_bytes") long sizeBytes) {

        public String backup_id() {
            return backupId;
        }

        public String original_path() {
            return originalPath;
        }

        public long size_bytes() {
            return sizeBytes;
        }
    }

    private static final int PREVIEW_LIMIT_CHARS = 256 * 1024;

    private final AppProperties properties;
    private final JsonFileStore store;
    private final ObjectMapper mapper;
    private final Tokens tokens;
    private final AuditService audit;

    private volatile List<Path> allowedRoots;
    private static final List<String> DENIED_ROOTS = List.of("/etc/shadow", "/etc/sudoers", "/root");

    public FileService(AppProperties properties, JsonFileStore store, ObjectMapper mapper,
                       Tokens tokens, AuditService audit) {
        this.properties = properties;
        this.store = store;
        this.mapper = mapper;
        this.tokens = tokens;
        this.audit = audit;
    }

    public List<Map<String, Object>> list(String rawPath) {
        Path dir = resolveAllowed(rawPath == null || rawPath.isBlank() ? firstRoot() : rawPath);
        if (!Files.exists(dir)) {
            throw AppException.notFound("path_not_found");
        }
        if (!Files.isDirectory(dir)) {
            throw AppException.validation("path is not a directory");
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        try (Stream<Path> children = Files.list(dir)) {
            children.forEach(child -> {
                Map<String, Object> item = new LinkedHashMap<>();
                try {
                    boolean isDir = Files.isDirectory(child);
                    item.put("name", child.getFileName().toString());
                    item.put("type", isDir ? "dir" : "file");
                    item.put("size_bytes", isDir ? 0 : Files.size(child));
                    item.put("modified_at", Files.getLastModifiedTime(child).toInstant().toString());
                    entries.add(item);
                } catch (IOException ignored) {
                    // skip unreadable child
                }
            });
        } catch (IOException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "failed to list directory",
                    Map.of("error", "path_not_allowed"));
        }
        entries.sort(Comparator.comparing((Map<String, Object> m) -> (String) m.get("type"))
                .thenComparing(m -> (String) m.get("name")));
        return entries;
    }

    public Map<String, Object> read(String rawPath) {
        Path path = resolveAllowed(rawPath);
        requireRegularFile(path);
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.length() > PREVIEW_LIMIT_CHARS) {
                throw AppException.validation("file too large for preview, use download");
            }
            return Map.of("path", path.toString(), "size_bytes", Files.size(path), "content", content);
        } catch (IOException e) {
            throw AppException.validation("failed to read file");
        }
    }

    public byte[] download(String rawPath) {
        Path path = resolveAllowed(rawPath);
        requireRegularFile(path);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw AppException.validation("failed to read file");
        }
    }

    public synchronized Map<String, Object> write(String rawPath, byte[] bytes) {
        Path path = resolveAllowed(rawPath);
        if (Files.isDirectory(path)) {
            throw AppException.validation("target is a directory");
        }
        backupIfExists(path);
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, bytes);
        } catch (IOException e) {
            throw AppException.validation("failed to write file");
        }
        audit.append("file", operator(), "write", path.toString(), "success",
                Map.of("size_bytes", bytes.length));
        return Map.of("status", "ok", "path", path.toString(), "size_bytes", bytes.length);
    }

    public synchronized Map<String, Object> delete(String rawPath) {
        Path path = resolveAllowed(rawPath);
        requireRegularFile(path);
        backupIfExists(path);
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw AppException.validation("failed to delete file");
        }
        audit.append("file", operator(), "delete", path.toString(), "success", Map.of());
        return Map.of("status", "ok", "path", path.toString());
    }

    public List<BackupRecord> backups(String rawPath) {
        List<BackupRecord> result = new ArrayList<>();
        for (String line : store.readLines(indexFile())) {
            try {
                var node = mapper.readValue(line, BackupRecord.class);
                if (rawPath == null || rawPath.isBlank()
                        || normalize(node.original_path()).equals(normalize(rawPath))) {
                    result.add(node);
                }
            } catch (Exception ignored) {
                // skip malformed backup index line
            }
        }
        result.sort(Comparator.comparing(BackupRecord::ts).reversed());
        return result;
    }

    public Map<String, Object> diff(String rawPath, String backupId) {
        Path path = resolveAllowed(rawPath);
        requireRegularFile(path);
        BackupRecord target = pickBackup(path, backupId);
        String current = readFileQuietly(path);
        String backupContent = readBackupBlob(target.file());
        List<Map<String, Object>> lines =
                TextDiff.diffLines(backupContent.split("\n", -1), current.split("\n", -1));
        long added = lines.stream().filter(l -> "add".equals(l.get("op"))).count();
        long removed = lines.stream().filter(l -> "remove".equals(l.get("op"))).count();
        return Map.of("backup_id", target.backup_id(), "lines", lines,
                "added", added, "removed", removed);
    }

    public synchronized Map<String, Object> restore(Map<String, Object> body) {
        String backupId = str(body.get("backup_id"));
        String rawPath = str(body.get("path"));
        if ((backupId == null || backupId.isBlank()) && (rawPath == null || rawPath.isBlank())) {
            throw AppException.validation("backup_id or path is required");
        }
        BackupRecord target = backupId != null && !backupId.isBlank()
                ? findBackupById(backupId)
                : pickBackup(resolveAllowed(rawPath), null);
        Path destination = resolveAllowed(target.original_path());
        byte[] content = readBackupBlobBytes(target.file());
        try {
            Files.write(destination, content);
        } catch (IOException e) {
            throw AppException.validation("failed to restore file");
        }
        audit.append("file", operator(), "restore", destination.toString(), "success",
                Map.of("backup_id", target.backup_id()));
        return Map.of("status", "ok", "path", destination.toString(),
                "restored_from", target.backup_id());
    }

    public Map<String, Object> prepareOperation(Map<String, Object> body) {
        String action = str(body.get("action"));
        String path = str(body.get("path"));
        if (!List.of("write", "delete").contains(action)) {
            throw AppException.validation("action must be write or delete");
        }
        Path resolved = resolveAllowed(path);
        boolean exists = Files.exists(resolved);
        long size = 0;
        Object payload = body.get("content");
        if ("write".equals(action) && payload != null) {
            size = String.valueOf(payload).getBytes(StandardCharsets.UTF_8).length;
        } else if (exists) {
            try {
                size = Files.size(resolved);
            } catch (IOException ignored) {
                // ignore size lookup failure
            }
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("plan_id", "plan-" + tokens.shortId());
        plan.put("status", "planned");
        plan.put("action", action);
        plan.put("path", resolved.toString());
        plan.put("exists", exists);
        plan.put("payload_size_bytes", size);
        plan.put("note", "approval executor removed in this simplified edition; "
                + "execute directly via /api/files/write or /api/files/delete");
        audit.append("file", operator(), "plan:" + action, resolved.toString(), "success", Map.of());
        return plan;
    }

    private void backupIfExists(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            String blobName = System.currentTimeMillis() + ".bak";
            Path blob = backupsDir().resolve(blobName);
            Files.copy(path, blob, StandardCopyOption.REPLACE_EXISTING);
            BackupRecord record = new BackupRecord(
                    "bkp-" + tokens.shortId(),
                    path.toString(),
                    blobName,
                    Instant.now(),
                    Files.size(path));
            store.appendLine(indexFile(), record);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BackupRecord pickBackup(Path originalPath, String backupId) {
        List<BackupRecord> matches = backups(originalPath.toString()).stream()
                .filter(b -> backupId == null || backupId.isBlank() || b.backup_id().equals(backupId))
                .toList();
        if (matches.isEmpty()) {
            throw AppException.notFound("backup_not_found");
        }
        return matches.get(0);
    }

    private BackupRecord findBackupById(String backupId) {
        return backups(null).stream()
                .filter(b -> b.backup_id().equals(backupId))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("backup_not_found"));
    }

    private String readBackupBlob(String fileName) {
        try {
            return Files.readString(backupsDir().resolve(fileName), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw AppException.validation("failed to read backup");
        }
    }

    private byte[] readBackupBlobBytes(String fileName) {
        try {
            return Files.readAllBytes(backupsDir().resolve(fileName));
        } catch (IOException e) {
            throw AppException.validation("failed to read backup");
        }
    }

    private Path backupsDir() {
        Path dir = Path.of(properties.dataDir()).resolve("file-backups");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }

    private Path indexFile() {
        return backupsDir().resolve("index.jsonl");
    }

    private static String readFileQuietly(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static void requireRegularFile(Path path) {
        if (!Files.exists(path)) {
            throw AppException.notFound("path_not_found");
        }
        if (!Files.isRegularFile(path)) {
            throw AppException.validation("path is not a regular file");
        }
    }

    private Path resolveAllowed(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw AppException.validation("path is required");
        }
        Path candidate = Path.of(rawPath).normalize().toAbsolutePath();
        boolean allowed = false;
        for (Path root : roots()) {
            if (candidate.startsWith(root)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw denied(candidate);
        }
        for (String deniedRoot : DENIED_ROOTS) {
            Path denied = Path.of(deniedRoot).normalize();
            if (candidate.startsWith(denied)) {
                throw denied(candidate);
            }
        }
        return candidate;
    }

    private static AppException denied(Path candidate) {
        return new AppException(ErrorCode.PERMISSION_DENIED, "path outside allowed roots: " + candidate,
                Map.of("error", "path_not_allowed"));
    }

    private List<Path> roots() {
        List<Path> cached = allowedRoots;
        if (cached != null) {
            return cached;
        }
        List<Path> parsed = new ArrayList<>();
        for (String root : properties.fileRoots().split(",")) {
            if (!root.isBlank()) {
                parsed.add(Path.of(root.trim()).normalize().toAbsolutePath());
            }
        }
        allowedRoots = List.copyOf(parsed);
        return allowedRoots;
    }

    private String firstRoot() {
        return roots().get(0).toString();
    }

    private static String normalize(String path) {
        return Path.of(path).normalize().toAbsolutePath().toString();
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
