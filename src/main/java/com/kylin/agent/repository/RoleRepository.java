package com.kylin.agent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kylin.agent.config.AppProperties;
import com.kylin.agent.security.RoleDefinition;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class RoleRepository {

    private static final TypeReference<List<RoleDefinition>> TYPE = new TypeReference<>() {
    };

    private final JsonFileStore store;
    private final Path path;

    public RoleRepository(JsonFileStore store, AppProperties properties) {
        this.store = store;
        this.path = Path.of(properties.authStore()).getParent().resolve("roles.json");
    }

    public List<RoleDefinition> loadAll() {
        return store.read(path, TYPE, ArrayList::new);
    }

    public void saveAll(List<RoleDefinition> roles) {
        store.write(path, roles);
    }

    public Optional<RoleDefinition> findByName(String name) {
        return loadAll().stream()
                .filter(r -> r.name().equalsIgnoreCase(name))
                .findFirst();
    }
}
