package com.linux.agent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Component
public class JsonFileStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileStore.class);

    private final ObjectMapper mapper;

    public JsonFileStore(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public <T> T read(Path path, TypeReference<T> type, Supplier<T> defaultSupplier) {
        if (!Files.exists(path)) {
            return defaultSupplier.get();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) {
                return defaultSupplier.get();
            }
            return mapper.readValue(bytes, type);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + path, e);
        }
    }

    public void write(Path path, Object value) {
        try {
            ensureParent(path);
            Path tmp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
            Files.write(tmp, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + path, e);
        }
    }

    public void appendLine(Path path, Object value) {
        try {
            ensureParent(path);
            String line = mapper.writeValueAsString(value);
            Files.write(path, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append " + path, e);
        }
    }

    public List<String> readLines(Path path) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> result = new ArrayList<>(lines.size());
            for (String line : lines) {
                if (!line.isBlank()) {
                    result.add(line);
                }
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + path, e);
        }
    }

    private void ensureParent(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
