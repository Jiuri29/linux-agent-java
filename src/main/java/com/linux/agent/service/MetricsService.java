package com.linux.agent.service;

import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private record Key(String method, String path, String status) {
    }

    private final ConcurrentHashMap<Key, AtomicLong> apiRequests = new ConcurrentHashMap<>();

    public void record(String method, String path, String status) {
        apiRequests.computeIfAbsent(new Key(method, path, status), k -> new AtomicLong()).incrementAndGet();
    }

    public String render(long assetsTotal, long usersTotal, long activeAlerts, long auditEvents) {
        StringBuilder sb = new StringBuilder();

        sb.append("# TYPE ops_agent_api_requests_total counter\n");
        apiRequests.entrySet().stream()
                .sorted((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()))
                .forEach(e -> sb.append("ops_agent_api_requests_total{method=\"")
                        .append(e.getKey().method()).append("\",path=\"")
                        .append(escape(e.getKey().path())).append("\",status=\"")
                        .append(e.getKey().status()).append("\"} ")
                        .append(e.getValue().get()).append('\n'));

        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        sb.append("# TYPE process_uptime_seconds gauge\nprocess_uptime_seconds ").append(uptimeSeconds).append('\n');

        long heapUsed = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        sb.append("# TYPE jvm_heap_used_bytes gauge\njvm_heap_used_bytes ").append(heapUsed).append('\n');

        sb.append("# TYPE ops_agent_assets_total gauge\nops_agent_assets_total ").append(assetsTotal).append('\n');
        sb.append("# TYPE ops_agent_users_total gauge\nops_agent_users_total ").append(usersTotal).append('\n');
        sb.append("# TYPE ops_agent_alerts_active_total gauge\nops_agent_alerts_active_total ")
                .append(activeAlerts).append('\n');
        sb.append("# TYPE ops_agent_audit_events_total gauge\nops_agent_audit_events_total ")
                .append(auditEvents).append('\n');
        return sb.toString();
    }

    private static String escape(String label) {
        return label.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
