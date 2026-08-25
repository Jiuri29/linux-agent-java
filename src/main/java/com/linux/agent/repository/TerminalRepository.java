package com.linux.agent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linux.agent.config.AppProperties;
import com.linux.agent.domain.TerminalTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Repository
public class TerminalRepository {

    private static final TypeReference<List<TerminalTemplate>> TEMPLATES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<HistoryEntry> ENTRY_TYPE = new TypeReference<>() {
    };

    public static class HistoryEntry {
        public String id;
        public String ts;
        public String operator;
        public String command;
        public int exit_code;
        public long duration_ms;
        public boolean allowed;
        public String reason;
    }

    private final AppProperties properties;
    private final JsonFileStore store;
    private final ObjectMapper mapper;
    private final Path dir;
    private final Path templatesPath;
    private final Path historyPath;

    public TerminalRepository(JsonFileStore store, ObjectMapper mapper, AppProperties properties) {
        this.properties = properties;
        this.store = store;
        this.mapper = mapper;
        this.dir = Path.of(properties.terminalLogDir());
        this.templatesPath = dir.resolve("templates.json");
        this.historyPath = dir.resolve("history.jsonl");
    }

    public List<TerminalTemplate> loadTemplates() {
        return store.read(templatesPath, TEMPLATES_TYPE, ArrayList::new);
    }

    public void saveTemplates(List<TerminalTemplate> templates) {
        store.write(templatesPath, templates);
    }

    public void appendHistory(HistoryEntry entry) {
        store.appendLine(historyPath, entry);
    }

    public List<HistoryEntry> readHistory(int limit) {
        List<String> lines = store.readLines(historyPath);
        List<HistoryEntry> entries = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && entries.size() < limit; i--) {
            try {
                entries.add(mapper.readValue(lines.get(i), ENTRY_TYPE));
            } catch (Exception ignored) {
                // skip malformed history line
            }
        }
        return entries;
    }

    public Path outputDir() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }

    public void writeOutput(String id, String content) {
        try {
            Files.writeString(outputDir().resolve(id + ".out"), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String readOutput(String id) {
        Path file = outputDir().resolve(id + ".out");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
