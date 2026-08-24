package com.kylin.agent.controller;

import com.kylin.agent.service.AssetService;
import com.kylin.agent.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping({"/api/assets", "/api/v1/assets"})
    public Object list(HttpServletRequest request) {
        Map<String, Object> result = assetService.list(request);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(result) : result;
    }

    @GetMapping({"/api/assets/detail", "/api/v1/assets/detail"})
    public Object detail(@RequestParam("id") String id, HttpServletRequest request) {
        Map<String, Object> asset = assetService.detail(id);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(asset) : asset;
    }

    @PostMapping({"/api/assets", "/api/v1/assets"})
    public Object create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> asset = assetService.create(body);
        return request.getRequestURI().startsWith("/api/v1/") ? ApiResponse.ok(asset) : asset;
    }

    @PostMapping("/api/assets/update")
    public Map<String, Object> update(@RequestBody Map<String, Object> body) {
        return assetService.update(body);
    }

    @PostMapping("/api/assets/delete")
    public Map<String, Object> delete(@RequestBody Map<String, Object> body) {
        String id = str(body.getOrDefault("id", body.get("asset_id")));
        assetService.delete(id);
        return Map.of("status", "ok");
    }

    @GetMapping("/api/assets/groups")
    public List<String> groups() {
        return assetService.groups();
    }

    @PostMapping("/api/assets/groups")
    public Map<String, Object> addGroup(@RequestBody Map<String, Object> body) {
        return assetService.addGroup(str(body.get("name")));
    }

    @PostMapping("/api/assets/groups/delete")
    public Map<String, Object> deleteGroup(@RequestBody Map<String, Object> body) {
        assetService.deleteGroup(str(body.get("name")));
        return Map.of("status", "ok");
    }

    @GetMapping("/api/assets/tags")
    public List<String> tags() {
        return assetService.tags();
    }

    @PostMapping("/api/assets/tags")
    public Map<String, Object> addTag(@RequestBody Map<String, Object> body) {
        return assetService.addTag(str(body.get("name")));
    }

    @PostMapping("/api/assets/tags/delete")
    public Map<String, Object> deleteTag(@RequestBody Map<String, Object> body) {
        assetService.deleteTag(str(body.get("name")));
        return Map.of("status", "ok");
    }

    @GetMapping("/api/assets/applications")
    public List<String> applications() {
        return assetService.applications();
    }

    @PostMapping("/api/assets/applications")
    public Map<String, Object> addApplication(@RequestBody Map<String, Object> body) {
        return assetService.addApplication(str(body.get("name")));
    }

    @PostMapping("/api/assets/services")
    public Map<String, Object> addPorts(@RequestBody Map<String, Object> body) {
        return assetService.addPorts(body);
    }

    @PostMapping("/api/assets/dependencies")
    public Map<String, Object> addDependency(@RequestBody Map<String, Object> body) {
        return assetService.addDependency(body);
    }

    @GetMapping("/api/assets/dependencies")
    public List<?> dependencies() {
        return assetService.dependencies();
    }

    @GetMapping("/api/assets/snapshots")
    public List<?> snapshots(@RequestParam(value = "asset_id", required = false) String assetId,
                             @RequestParam(value = "host_id", required = false) String hostId) {
        return assetService.snapshots(assetId, hostId);
    }

    @PostMapping("/api/assets/sync")
    public Map<String, Object> sync(@RequestBody(required = false) Map<String, Object> body) {
        String source = body == null ? "manual" : str(body.getOrDefault("source", "manual"));
        return assetService.sync(source);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
