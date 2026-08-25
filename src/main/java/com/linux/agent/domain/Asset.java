package com.linux.agent.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Asset {

    public String id;
    public String hostname;
    public String ip;
    public String os;
    public String arch;
    public String environment = "production";
    public String status = "online";
    public List<String> tags = new ArrayList<>();
    public List<String> groups = new ArrayList<>();
    public List<String> applications = new ArrayList<>();
    public List<AssetPort> ports = new ArrayList<>();
    public Instant createdAt;
    public Instant updatedAt;
}
