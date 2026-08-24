package com.kylin.agent.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TextDiff {

    private TextDiff() {
    }

    public static List<Map<String, Object>> diffLines(String[] from, String[] to) {
        int n = from.length;
        int m = to.length;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                dp[i][j] = from[i].equals(to[j])
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (from[i].equals(to[j])) {
                out.add(line("same", from[i]));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                out.add(line("remove", from[i]));
                i++;
            } else {
                out.add(line("add", to[j]));
                j++;
            }
        }
        while (i < n) {
            out.add(line("remove", from[i]));
            i++;
        }
        while (j < m) {
            out.add(line("add", to[j]));
            j++;
        }
        return out;
    }

    private static Map<String, Object> line(String op, String text) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("op", op);
        map.put("text", text);
        return map;
    }
}
