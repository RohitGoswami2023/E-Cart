package com.ecommerce.project.benchmark;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BenchmarkReport {
    private static final Map<String, Map<String, Object>> METRICS = new ConcurrentHashMap<>();

    private BenchmarkReport() {
    }

    public static void record(String suite, boolean passed, long durationMs, Integer stockStart, Integer stockEnd,
                              Integer successCount, Integer failCount) {
        Map<String, Object> row = METRICS.computeIfAbsent(suite, k -> new ConcurrentHashMap<>());
        row.put("suite", suite);
        row.put("passed", passed);
        row.put("durationMs", durationMs);
        if (stockStart != null) row.put("stockStart", stockStart);
        if (stockEnd != null) row.put("stockEnd", stockEnd);
        if (successCount != null) row.put("successCount", successCount);
        if (failCount != null) row.put("failCount", failCount);
    }

    public static void putExtra(String suite, String key, Object value) {
        if (value != null) {
            Map<String, Object> row = METRICS.computeIfAbsent(suite, k -> new ConcurrentHashMap<>());
            row.put(key, value);
        }
    }

    public static Map<String, Map<String, Object>> snapshot() {
        return METRICS;
    }
}
