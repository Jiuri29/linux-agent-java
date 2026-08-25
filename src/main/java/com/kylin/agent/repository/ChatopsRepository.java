package com.kylin.agent.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kylin.agent.config.AppProperties;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ChatopsRepository {

    public static class ChatSession {
        public String id;
        @JsonProperty("created_at")
        public Instant createdAt;
        public List<Map<String, Object>> messages = new ArrayList<>();
        @JsonProperty("pending_actions")
        public List<Map<String, Object>> pendingActions = new ArrayList<>();
    }

    private static final TypeReference<List<ChatSession>> TYPE = new TypeReference<>() {
    };

    private final JsonFileStore store;
    private final Path path;

    public ChatopsRepository(JsonFileStore store, AppProperties properties) {
        this.store = store;
        this.path = Path.of(properties.chatopsStore());
    }

    public List<ChatSession> loadAll() {
        return store.read(path, TYPE, ArrayList::new);
    }

    public void saveAll(List<ChatSession> sessions) {
        store.write(path, sessions);
    }

    public ChatSession create() {
        ChatSession session = new ChatSession();
        session.id = "chs-" + System.currentTimeMillis();
        session.createdAt = Instant.now();
        List<ChatSession> all = new ArrayList<>(loadAll());
        all.add(session);
        saveAll(all);
        return session;
    }

    public static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        message.put("ts", Instant.now().toString());
        return message;
    }
}
