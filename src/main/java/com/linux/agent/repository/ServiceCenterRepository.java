package com.linux.agent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.linux.agent.config.AppProperties;
import com.linux.agent.domain.ChangeRecord;
import com.linux.agent.domain.ConfigVersion;
import com.linux.agent.domain.ServiceEntry;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ServiceCenterRepository {

    public static class ServiceCenterDocument {
        public List<ServiceEntry> services = new ArrayList<>();
        public List<ConfigVersion> configVersions = new ArrayList<>();
        public List<ChangeRecord> changes = new ArrayList<>();
    }

    private static final TypeReference<ServiceCenterDocument> TYPE = new TypeReference<>() {
    };

    private final JsonFileStore store;
    private final Path path;

    public ServiceCenterRepository(JsonFileStore store, AppProperties properties) {
        this.store = store;
        this.path = Path.of(properties.serviceCenterStore());
    }

    public ServiceCenterDocument load() {
        return store.read(path, TYPE, ServiceCenterDocument::new);
    }

    public void save(ServiceCenterDocument document) {
        store.write(path, document);
    }
}
