package com.linux.agent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.linux.agent.config.AppProperties;
import com.linux.agent.domain.McpPlugin;
import com.linux.agent.domain.McpTool;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Repository
public class McpRepository {

    public static class McpDocument {
        public List<McpTool> tools = new ArrayList<>();
        public List<McpPlugin> plugins = new ArrayList<>();
    }

    private static final TypeReference<McpDocument> DOC_TYPE = new TypeReference<>() {
    };

    private final JsonFileStore store;
    private final Path registryPath;
    private final Path invocationLogPath;

    public McpRepository(JsonFileStore store, AppProperties properties) {
        this.store = store;
        this.registryPath = Path.of(properties.mcpStore());
        this.invocationLogPath = Path.of(properties.mcpInvocationLog());
    }

    public McpDocument load() {
        return store.read(registryPath, DOC_TYPE, McpDocument::new);
    }

    public void save(McpDocument document) {
        store.write(registryPath, document);
    }

    public void appendInvocation(Object entry) {
        store.appendLine(invocationLogPath, entry);
    }
}
