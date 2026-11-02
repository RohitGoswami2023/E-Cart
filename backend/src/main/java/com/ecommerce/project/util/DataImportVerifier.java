package com.ecommerce.project.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verifies that the DB row counts match the CSV file row counts (minus the header).
 *
 * <p>Run with:
 * <pre>
 *   .\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--verify-import
 * </pre>
 */
@Component
@Order(2)
public class DataImportVerifier implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataImportVerifier.class);

    private static final Path DATA_DIR = Paths.get(System.getProperty("user.dir"))
            .getParent()
            .resolve("data");

    /** Maps CSV base-name → actual DB table name */
    private static final Map<String, String> TABLE_MAP = new LinkedHashMap<>();

    static {
        TABLE_MAP.put("products",           "products");
        TABLE_MAP.put("ecommerce_users_1m", "users");
        TABLE_MAP.put("orders",             "orders");
        TABLE_MAP.put("payments",           "payments");
        TABLE_MAP.put("order_items",        "order_items");
        TABLE_MAP.put("carts",              "carts");
    }

    private final JdbcTemplate jdbc;

    public DataImportVerifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) throws Exception {
        if (Arrays.stream(args).noneMatch("--verify-import"::equals)) {
            return;
        }

        log.info("=== DataImportVerifier – comparing CSV counts vs DB ===");
        boolean allOk = true;

        for (Map.Entry<String, String> entry : TABLE_MAP.entrySet()) {
            String csvKey  = entry.getKey();
            String table   = entry.getValue();

            // Find the actual CSV file (handles nested-folder pattern)
            Path csvPath = findCsv(csvKey);
            if (csvPath == null) {
                log.warn("⚠️  CSV not found for key '{}' – skipping", csvKey);
                continue;
            }

            long csvRows = countCsvRows(csvPath);
            Long dbRows  = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            if (dbRows == null) dbRows = 0L;

            if (csvRows == dbRows) {
                log.info("✅ '{}': CSV={} rows, DB={} rows – MATCH", table, csvRows, dbRows);
            } else {
                log.warn("⚠️  '{}': CSV={} rows, DB={} rows – MISMATCH (delta: {})", table, csvRows, dbRows, csvRows - dbRows);
                allOk = false;
            }
        }

        if (allOk) {
            log.info("=== All tables match ✅ ===");
        } else {
            log.warn("=== Some tables have mismatches – review warnings above ===");
        }
    }

    private Path findCsv(String key) throws IOException {
        // Pattern: data/<key>.csv/<key>.csv  OR  data/<key>.csv
        Path nested = DATA_DIR.resolve(key + ".csv").resolve(key + ".csv");
        if (Files.isRegularFile(nested)) return nested;
        Path flat = DATA_DIR.resolve(key + ".csv");
        if (Files.isRegularFile(flat)) return flat;
        return null;
    }

    private long countCsvRows(Path csv) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(csv)) {
            return br.lines().skip(1).count(); // skip header
        }
    }
}

