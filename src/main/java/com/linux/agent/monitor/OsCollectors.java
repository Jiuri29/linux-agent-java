package com.linux.agent.monitor;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OsCollectors {

    private static volatile long[] prevCpuTicks;

    public Map<String, Object> readOnlySnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("timestamp", Instant.now().toString());
        snapshot.put("hostname", hostname());
        snapshot.put("os", Map.of(
                "name", System.getProperty("os.name"),
                "version", System.getProperty("os.version"),
                "arch", System.getProperty("os.arch")));
        snapshot.put("kernel", readFileOrNull("/proc/sys/kernel/osrelease"));
        snapshot.put("uptime_seconds", uptimeSeconds());
        snapshot.put("cpu", cpuInfo());
        snapshot.put("memory", memoryInfo());
        snapshot.put("load", loadInfo());
        snapshot.put("disks", diskInfo());
        return snapshot;
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private Object uptimeSeconds() {
        String content = readFileOrNull("/proc/uptime");
        if (content == null) {
            return null;
        }
        try {
            return (long) Double.parseDouble(content.trim().split("\\s+")[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> cpuInfo() {
        Map<String, Object> cpu = new LinkedHashMap<>();
        String model = null;
        String procStat = readFileOrNull("/proc/stat");
        if (Files.exists(Path.of("/proc/cpuinfo"))) {
            try {
                for (String line : Files.readAllLines(Path.of("/proc/cpuinfo"))) {
                    if (line.startsWith("model name")) {
                        model = line.substring(line.indexOf(':') + 1).trim();
                        break;
                    }
                }
            } catch (IOException ignored) {
                // /proc/cpuinfo unreadable
            }
        }
        cpu.put("model", model);
        cpu.put("logical_cores", Runtime.getRuntime().availableProcessors());
        cpu.put("usage_percent", cpuUsagePercent(procStat));
        return cpu;
    }

    private Double cpuUsagePercent(String procStat) {
        if (procStat == null) {
            return systemCpuLoadPercent();
        }
        try {
            String[] parts = procStat.split("\\s+");
            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]) + Long.parseLong(parts[5]);
            long irq = Long.parseLong(parts[6]) + Long.parseLong(parts[7]);
            long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0;
            long total = user + nice + system + idle + irq + steal;
            long[] prev = prevCpuTicks;
            prevCpuTicks = new long[]{idle, total};
            if (prev == null || total <= prev[1]) {
                return null;
            }
            double usedRatio = (double) ((total - prev[1]) - (idle - prev[0])) / (total - prev[1]);
            return Math.round(Math.max(0, Math.min(100, usedRatio * 100)) * 10) / 10.0;
        } catch (Exception e) {
            return systemCpuLoadPercent();
        }
    }

    private Double systemCpuLoadPercent() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double load = os.getSystemCpuLoad();
            if (load < 0) {
                return null;
            }
            return Math.round(Math.min(100, Math.max(0, load * 100)) * 10) / 10.0;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> memoryInfo() {
        Map<String, Object> memory = new LinkedHashMap<>();
        Long totalKb = null;
        Long availableKb = null;
        String meminfo = readFileOrNull("/proc/meminfo");
        if (meminfo != null) {
            for (String line : meminfo.split("\n")) {
                if (line.startsWith("MemTotal:")) {
                    totalKb = parseKb(line);
                } else if (line.startsWith("MemAvailable:")) {
                    availableKb = parseKb(line);
                }
            }
        }
        if (totalKb == null) {
            try {
                com.sun.management.OperatingSystemMXBean os =
                        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                totalKb = os.getTotalPhysicalMemorySize() / 1024;
                availableKb = os.getFreePhysicalMemorySize() / 1024;
            } catch (Exception e) {
                memory.put("usage_percent", null);
                return memory;
            }
        }
        memory.put("total_kb", totalKb);
        memory.put("available_kb", availableKb);
        if (totalKb != null && totalKb > 0 && availableKb != null) {
            double used = (double) (totalKb - availableKb) / totalKb * 100;
            memory.put("usage_percent", Math.round(Math.min(100, Math.max(0, used)) * 10) / 10.0);
        } else {
            memory.put("usage_percent", null);
        }
        return memory;
    }

    private Map<String, Object> loadInfo() {
        Map<String, Object> load = new LinkedHashMap<>();
        String loadavg = readFileOrNull("/proc/loadavg");
        if (loadavg != null) {
            String[] parts = loadavg.trim().split("\\s+");
            try {
                load.put("load1", Double.parseDouble(parts[0]));
                load.put("load5", Double.parseDouble(parts[1]));
                load.put("load15", Double.parseDouble(parts[2]));
                return load;
            } catch (Exception ignored) {
                // malformed /proc/loadavg
            }
        }
        double avg = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        load.put("load1", avg < 0 ? null : avg);
        load.put("load5", null);
        load.put("load15", null);
        return load;
    }

    private List<Map<String, Object>> diskInfo() {
        List<Map<String, Object>> disks = new ArrayList<>();
        String output = exec("df -kP");
        if (output == null) {
            return fallbackDisks();
        }
        for (String line : output.split("\n")) {
            String[] cols = line.trim().split("\\s+");
            if (cols.length < 6 || !cols[5].startsWith("/") || !cols[1].matches("\\d+")) {
                continue;
            }
            try {
                long totalKb = Long.parseLong(cols[1]);
                long usedKb = Long.parseLong(cols[2]);
                Map<String, Object> disk = new LinkedHashMap<>();
                disk.put("filesystem", cols[0]);
                disk.put("mounted_on", cols[5]);
                disk.put("total_kb", totalKb);
                disk.put("used_kb", usedKb);
                disk.put("available_kb", Long.parseLong(cols[3]));
                disk.put("usage_percent", totalKb > 0 ? Math.round((double) usedKb / totalKb * 1000) / 10.0 : 0);
                disks.add(disk);
            } catch (NumberFormatException ignored) {
                // skip malformed df line
            }
        }
        return disks;
    }

    private List<Map<String, Object>> fallbackDisks() {
        List<Map<String, Object>> disks = new ArrayList<>();
        for (java.io.File root : java.io.File.listRoots()) {
            long total = root.getTotalSpace();
            if (total <= 0) {
                continue;
            }
            long usable = root.getUsableSpace();
            long used = total - usable;
            Map<String, Object> disk = new LinkedHashMap<>();
            disk.put("filesystem", root.getPath());
            disk.put("mounted_on", root.getPath());
            disk.put("total_kb", total / 1024);
            disk.put("used_kb", used / 1024);
            disk.put("available_kb", usable / 1024);
            disk.put("usage_percent", Math.round((double) used / total * 1000) / 10.0);
            disks.add(disk);
        }
        return disks;
    }

    private static Long parseKb(String line) {
        try {
            String value = line.substring(line.indexOf(':') + 1).trim().split("\\s+")[0];
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String readFileOrNull(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            return null;
        }
    }

    private static String exec(String command) {
        com.linux.agent.util.ProcessExec.Result result =
                com.linux.agent.util.ProcessExec.run(new ProcessBuilder(command.split("\\s+")), 5, java.util.concurrent.TimeUnit.SECONDS);
        if (result.timedOut() || result.failure() != null) {
            return null;
        }
        return result.mergedOutput();
    }
}
