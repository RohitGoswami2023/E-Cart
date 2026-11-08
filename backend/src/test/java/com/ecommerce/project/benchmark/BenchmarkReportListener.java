package com.ecommerce.project.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BenchmarkReportListener implements TestExecutionListener {
    private final List<Map<String, Object>> tests = new ArrayList<>();
    private long started;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        started = System.currentTimeMillis();
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (!testIdentifier.isTest()) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", testIdentifier.getDisplayName());
        row.put("id", testIdentifier.getUniqueId());
        row.put("status", result.getStatus().name());
        tests.add(row);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try {
            long failed = tests.stream().filter(t -> "FAILED".equals(t.get("status"))).count();
            long passed = tests.stream().filter(t -> "SUCCESSFUL".equals(t.get("status"))).count();
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("generatedAt", Instant.now().toString());
            report.put("durationMs", System.currentTimeMillis() - started);
            report.put("total", tests.size());
            report.put("passed", passed);
            report.put("failed", failed);
            report.put("passRate", tests.isEmpty() ? 0 : (passed * 100.0 / tests.size()));
            report.put("tests", tests);
            report.put("metrics", BenchmarkReport.snapshot());

            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            Path backend = Path.of("target", "benchmark-report.json");
            Files.createDirectories(backend.getParent());
            mapper.writeValue(backend.toFile(), report);

            Path frontend = Path.of("..", "frontend", "public", "benchmark-report.json");
            Files.createDirectories(frontend.getParent());
            mapper.writeValue(frontend.toFile(), report);
        } catch (Exception ignored) {
        }
    }
}
