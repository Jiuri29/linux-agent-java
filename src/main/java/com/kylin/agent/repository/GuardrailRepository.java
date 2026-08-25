package com.kylin.agent.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kylin.agent.config.AppProperties;
import com.kylin.agent.domain.GuardrailExemption;
import com.kylin.agent.domain.GuardrailRule;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Repository
public class GuardrailRepository {

    public static class GuardrailDocument {
        public List<GuardrailRule> rules = new ArrayList<>();
        public List<GuardrailExemption> exemptions = new ArrayList<>();
    }

    public static class ApprovalRequest {
        public String id;
        public String ts;
        public String operator;
        public String intent;
        public String overall;
        @JsonProperty("decisions")
        public List<DecisionLine> decisions = new ArrayList<>();
    }

    public static class DecisionLine {
        public String command;
        public String decision;
        public String matched_rule;
        public String severity;
        public String exempt_by;
    }

    private static final TypeReference<GuardrailDocument> DOC_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<ApprovalRequest> APPROVAL_TYPE = new TypeReference<>() {
    };

    private final JsonFileStore store;
    private final ObjectMapper mapper;
    private final Path rulesPath;
    private final Path approvalsPath;

    public GuardrailRepository(JsonFileStore store, ObjectMapper mapper, AppProperties properties) {
        this.store = store;
        this.mapper = mapper;
        this.rulesPath = Path.of(properties.guardrailsStore());
        this.approvalsPath = Path.of(properties.guardrailsApprovalStore());
    }

    public GuardrailDocument load() {
        return store.read(rulesPath, DOC_TYPE, GuardrailDocument::new);
    }

    public void save(GuardrailDocument document) {
        store.write(rulesPath, document);
    }

    public void appendApproval(ApprovalRequest request) {
        store.appendLine(approvalsPath, request);
    }

    public List<ApprovalRequest> readApprovals(int limit) {
        List<String> lines = store.readLines(approvalsPath);
        List<ApprovalRequest> result = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && result.size() < limit; i--) {
            try {
                result.add(mapper.readValue(lines.get(i), APPROVAL_TYPE));
            } catch (Exception ignored) {
                // skip malformed approval line
            }
        }
        return result;
    }
}
