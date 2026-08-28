package com.linux.agent.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ProcessExec {

    public record Result(int exitCode, String stdout, String stderr, boolean timedOut, Exception failure) {

        public String mergedOutput() {
            StringBuilder sb = new StringBuilder();
            if (stdout != null && !stdout.isEmpty()) {
                sb.append(stdout);
            }
            if (stderr != null && !stderr.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(stderr);
            }
            return sb.toString();
        }

        public boolean success() {
            return !timedOut && failure == null && exitCode == 0;
        }
    }

    private ProcessExec() {
    }

    public static Result run(ProcessBuilder builder, long timeout, TimeUnit unit) {
        Process process = null;
        try {
            process = builder.start();
            final Process proc = process;
            CompletableFuture<String> stdout =
                    CompletableFuture.supplyAsync(() -> drain(proc.getInputStream()));
            CompletableFuture<String> stderr =
                    CompletableFuture.supplyAsync(() -> drain(proc.getErrorStream()));
            boolean finished = process.waitFor(timeout, unit);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                return new Result(-1, "", "", true, null);
            }
            return new Result(process.exitValue(),
                    stdout.get(5, TimeUnit.SECONDS), stderr.get(5, TimeUnit.SECONDS), false, null);
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new Result(-1, "", "", false, e);
        }
    }

    private static String drain(InputStream in) {
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
