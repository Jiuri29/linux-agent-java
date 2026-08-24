package com.kylin.agent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kylin.agent.config.AppProperties;
import com.kylin.agent.domain.Asset;
import com.kylin.agent.domain.AssetDependency;
import com.kylin.agent.domain.AssetSnapshot;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AssetRepository {

    public static class AssetDocument {
        public List<Asset> assets = new ArrayList<>();
        public List<String> groups = new ArrayList<>();
        public List<String> tags = new ArrayList<>();
        public List<String> applications = new ArrayList<>();
        public List<AssetDependency> dependencies = new ArrayList<>();
        public List<AssetSnapshot> snapshots = new ArrayList<>();
    }

    private static final TypeReference<AssetDocument> TYPE = new TypeReference<>() {
    };

    private final JsonFileStore store;
    private final Path path;

    public AssetRepository(JsonFileStore store, AppProperties properties) {
        this.store = store;
        this.path = Path.of(properties.assetStore());
    }

    public AssetDocument load() {
        return store.read(path, TYPE, AssetDocument::new);
    }

    public void save(AssetDocument document) {
        store.write(path, document);
    }
}
