package com.kylin.agent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kylin.agent.config.AppProperties;
import com.kylin.agent.domain.AgentApiConfig;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;

@Repository
public class AgentConfigRepository {

    private static final TypeReference<AgentApiConfig> TYPE = new TypeReference<>() {
    };

    private final JsonFileStore store;
    private final Path path;

    public AgentConfigRepository(JsonFileStore store, AppProperties properties) {
        this.store = store;
        this.path = Path.of(properties.apiConfig());
    }

    public AgentApiConfig load() {
        return store.read(path, TYPE, () -> null);
    }

    public void save(AgentApiConfig config) {
        store.write(path, config);
    }
}
