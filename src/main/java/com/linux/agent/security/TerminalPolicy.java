package com.linux.agent.security;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class TerminalPolicy {

    private static final Set<String> METACHARACTERS = Set.of(";", "&&", "||", "|", ">", "<", "`", "$(", "&");

    private static final Set<String> SIMPLE_COMMANDS = Set.of(
            "uname", "uptime", "date", "whoami", "id", "hostname",
            "free", "df", "ps", "lsblk", "lscpu", "ss");

    private static final Set<String> SYSTEMCTL_READONLY = Set.of(
            "status", "show", "is-active", "is-enabled", "list-units", "list-unit-files");

    private static final Set<String> IP_SUBCOMMANDS = Set.of("addr", "route", "link");

    public Verdict evaluate(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return Verdict.deny("command is required");
        }
        String trimmed = commandLine.trim();
        for (String meta : METACHARACTERS) {
            if (trimmed.contains(meta)) {
                return Verdict.deny("shell metacharacters are not allowed");
            }
        }
        String[] tokens = trimmed.split("\\s+");
        String cmd = tokens[0].toLowerCase(Locale.ROOT);
        if (SIMPLE_COMMANDS.contains(cmd)) {
            for (int i = 1; i < tokens.length; i++) {
                String token = tokens[i];
                boolean isFlag = token.startsWith("-");
                boolean isSimpleValue = token.matches("^[a-zA-Z0-9_./=-]+$");
                if (!isFlag && !isSimpleValue) {
                    return Verdict.deny("argument not allowed: " + token);
                }
            }
            return Verdict.allow();
        }
        switch (cmd) {
            case "systemctl" -> {
                if (tokens.length < 2 || !SYSTEMCTL_READONLY.contains(tokens[1])) {
                    return Verdict.deny("only read-only systemctl subcommands allowed");
                }
                return Verdict.allow();
            }
            case "ip" -> {
                if (tokens.length < 2 || !IP_SUBCOMMANDS.contains(tokens[1])) {
                    return Verdict.deny("only ip addr/route/link allowed");
                }
                return Verdict.allow();
            }
            case "journalctl" -> {
                if (trimmed.contains("--no-pager")) {
                    return Verdict.allow();
                }
                return Verdict.deny("journalctl requires --no-pager");
            }
            default -> {
                return Verdict.deny("command not in allowlist: " + cmd);
            }
        }
    }

    public record Verdict(boolean allowed, String reason) {

        static Verdict allow() {
            return new Verdict(true, null);
        }

        static Verdict deny(String reason) {
            return new Verdict(false, reason);
        }
    }
}
