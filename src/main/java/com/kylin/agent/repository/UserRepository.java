package com.kylin.agent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kylin.agent.config.AppProperties;
import com.kylin.agent.security.UserAccount;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private static final TypeReference<List<UserAccount>> TYPE = new TypeReference<>() {
    };

    private final JsonFileStore store;
    private final Path path;

    public UserRepository(JsonFileStore store, AppProperties properties) {
        this.store = store;
        this.path = Path.of(properties.authStore());
    }

    public List<UserAccount> loadAll() {
        return store.read(path, TYPE, ArrayList::new);
    }

    public void saveAll(List<UserAccount> users) {
        store.write(path, users);
    }

    public boolean isEmpty() {
        return loadAll().isEmpty();
    }

    public Optional<UserAccount> findByUsername(String username) {
        return loadAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().equals(username))
                .findFirst();
    }
}
