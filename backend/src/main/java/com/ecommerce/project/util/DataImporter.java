package com.ecommerce.project.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

@Component
@Order(1)
public class DataImporter implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataImporter.class);

    private static final int BATCH_SIZE = 2_000;

    private final JdbcTemplate jdbc;

    private static final Path DATA_DIR = Paths.get(System.getProperty("user.dir"))
            .getParent()
            .resolve("data");

    public DataImporter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final Map<String, CsvMapping> MAPPINGS = new LinkedHashMap<>();

    static {
        MAPPINGS.put("products", new CsvMapping(
                "products",
                new String[]{"product_name", "product_name", "stock_quantity", "price_inr", "mrp_inr", "price_inr"},
                new String[]{"product_name", "description",  "quantity",       "price",     "special_price", "discount"},
                null
        ));

        MAPPINGS.put("ecommerce_users_1m", new CsvMapping(
                "users",
                new String[]{"first_name", "email", "first_name"},
                new String[]{"username",   "email", "password"},
                null
        ));

        MAPPINGS.put("orders", new CsvMapping(
                "orders",
                new String[]{"user_id", "order_date", "order_status", "total_inr"},
                new String[]{"email",   "order_date", "order_status", "total_amount"},
                null
        ));

        MAPPINGS.put("payments", new CsvMapping(
                "payments",
                new String[]{"payment_method", "payment_status", "transaction_reference"},
                new String[]{"payment_method",  "pg_status",      "pg_payment_id"},
                null
        ));
    }

    @Override
    public void run(String... args) throws Exception {
        if (Arrays.stream(args).noneMatch("--import-data"::equals)) {
            return;
        }

        log.info("=== DataImporter starting – scanning {} ===", DATA_DIR);

        if (!Files.isDirectory(DATA_DIR)) {
            log.error("Data folder not found: {}", DATA_DIR);
            return;
        }

        List<Path> csvFiles = new ArrayList<>();
        try (DirectoryStream<Path> top = Files.newDirectoryStream(DATA_DIR)) {
            for (Path entry : top) {
                if (Files.isDirectory(entry)) {
                    Path nested = entry.resolve(entry.getFileName());
                    if (Files.isRegularFile(nested) && nested.toString().endsWith(".csv")) {
                        csvFiles.add(nested);
                    }
                } else if (Files.isRegularFile(entry) && entry.toString().endsWith(".csv")) {
                    csvFiles.add(entry);
                }
            }
        }

        log.info("Found {} CSV file(s): {}", csvFiles.size(), csvFiles);
        for (Path csv : csvFiles) {
            String key = baseName(csv);
            if (MAPPINGS.containsKey(key)) {
                importFile(csv, MAPPINGS.get(key));
            } else {
                log.warn("No mapping defined for '{}' – skipping", key);
            }
        }

        log.info("=== DataImporter finished ===");
    }

    void importFile(Path csv, CsvMapping mapping) throws IOException, SQLException {
        log.info("Importing {} → table '{}'", csv.getFileName(), mapping.table);
        Connection conn = Objects.requireNonNull(jdbc.getDataSource()).getConnection();
        try (conn; BufferedReader br = Files.newBufferedReader(csv)) {

            conn.setAutoCommit(false);
            String header = br.readLine();
            if (header == null) {
                log.warn("Empty file: {}", csv);
                return;
            }

            String[] csvHeaders = header.split(",", -1);
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < csvHeaders.length; i++) {
                colIndex.put(csvHeaders[i].trim().toLowerCase(), i);
            }

            String sql = buildInsertSql(mapping);
            log.debug("SQL: {}", sql);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                String line;
                long rowCount = 0;
                long batchCount = 0;
                final String PLACEHOLDER_HASH = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8RIF4h87IHAbEWWlS2";

                while ((line = br.readLine()) != null) {
                    String[] values = splitCsv(line);
                    try {
                        bindRow(ps, mapping, colIndex, values, PLACEHOLDER_HASH);
                        ps.addBatch();
                        batchCount++;
                        rowCount++;
                    } catch (Exception e) {
                        log.warn("Skipping malformed row: {} | Reason: {}", line.substring(0, Math.min(80, line.length())), e.getMessage());
                    }

                    if (batchCount >= BATCH_SIZE) {
                        ps.executeBatch();
                        conn.commit();
                        batchCount = 0;
                        if (rowCount % 100_000 == 0) {
                            log.info("  ... {} rows committed so far", rowCount);
                        }
                    }
                }

                if (batchCount > 0) {
                    ps.executeBatch();
                    conn.commit();
                }
                log.info("Imported {} rows → '{}'", rowCount, mapping.table);

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ex) { log.error("Rollback failed", ex); }
                log.error("Failed importing '{}' – rolled back. Cause: {}", csv.getFileName(), e.getMessage());
            }

        } catch (SQLException e) {
            log.error("Could not open DB connection for '{}'. Cause: {}", csv.getFileName(), e.getMessage());
        }
    }

    private String buildInsertSql(CsvMapping m) {
        String cols = String.join(", ", m.dbColumns);
        String placeholders = String.join(", ", Collections.nCopies(m.dbColumns.length, "?"));
        return "INSERT INTO " + m.table + " (" + cols + ") VALUES (" + placeholders + ") ON CONFLICT DO NOTHING";
    }

    private void bindRow(PreparedStatement ps, CsvMapping m, Map<String, Integer> idx,
                         String[] values, String passwordHash) throws SQLException {
        for (int i = 0; i < m.csvColumns.length; i++) {
            String csvCol = m.csvColumns[i].toLowerCase();
            Integer pos = idx.get(csvCol);
            String val = (pos != null && pos < values.length) ? values[pos].trim() : null;

            if ("users".equals(m.table) && m.dbColumns[i].equals("password")) {
                ps.setString(i + 1, passwordHash);
            } else if ("products".equals(m.table) && m.dbColumns[i].equals("discount")) {
                ps.setObject(i + 1, 0.0, Types.OTHER);
            } else if (val == null || val.isEmpty()) {
                ps.setNull(i + 1, Types.NULL);
            } else {
                ps.setObject(i + 1, val, Types.OTHER);
            }
        }
    }

    private String[] splitCsv(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }

    private String baseName(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    record CsvMapping(String table, String[] csvColumns, String[] dbColumns, Object unused) {}
}
