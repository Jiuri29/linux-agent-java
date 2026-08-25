package com.kylin.agent.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class McpTool {

    public static class ParamDef {
        public String name;
        public String type = "string";
        public boolean required;

        public ParamDef() {
        }

        public ParamDef(String name, String type, boolean required) {
            this.name = name;
            this.type = type;
            this.required = required;
        }
    }

    public String name;
    public String description;
    public List<ParamDef> params = new ArrayList<>();
    public boolean enabled = true;
    public boolean builtin;
    @JsonProperty("plugin_id")
    public String pluginId;
}
