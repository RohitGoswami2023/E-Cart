package com.ecommerce.project.util;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DataImporter}.
 *
 * <p>Runs against the real local PostgreSQL. Uses a dedicated staging table
 * {@code import_test_staging} so it never touches production data.
 *
 * <p>Pre-conditions:
 * <ul>
 *   <li>PostgreSQL must be running locally on port 5432.</li>
 *   <li>DB credentials in application.properties must be reachable.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataImporterTest {

    @Autowired
    private DataImporter importer;

    @Autowired
    private JdbcTemplate jdbc;

    private Path tempDir;
    private Path tempCsv;

    @BeforeEach
    void setUpTable() throws IOException {
        // Create staging table if not exists, then truncate for isolation
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS import_test_staging (
                    id          BIGSERIAL PRIMARY KEY,
                    product_name VARCHAR(255) UNIQUE,
                    description  VARCHAR(500),
                    price        NUMERIC(12,2)
                )
                """);
        jdbc.execute("TRUNCATE import_test_staging RESTART IDENTITY");

        tempDir = Files.createTempDirectory("import-test-");
        tempCsv = tempDir.resolve("import_test_staging.csv");
    }

    @AfterEach
    void cleanup() throws IOException {
        jdbc.execute("DROP TABLE IF EXISTS import_test_staging");
        if (tempCsv != null) Files.deleteIfExists(tempCsv);
        if (tempDir != null) Files.deleteIfExists(tempDir);
    }

    @AfterAll
    static void dropStagingTable(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("DROP TABLE IF EXISTS import_test_staging");
    }

    // -------------------------------------------------------------------------
    // Test 1 – Normal import inserts the expected number of rows
    // -------------------------------------------------------------------------
    @Test
    @Order(1)
    void importCsv_normal_insertsExpectedRows() throws Exception {
        Files.writeString(tempCsv,
                "product_name,description,price\n" +
                "Apple Phone,Flagship smartphone,79999\n" +
                "Budget Laptop,Everyday laptop,45000\n" +
                "Wireless Mouse,Ergonomic mouse,1299\n");

        DataImporter.CsvMapping mapping = new DataImporter.CsvMapping(
                "import_test_staging",
                new String[]{"product_name", "description", "price"},
                new String[]{"product_name", "description", "price"},
                null
        );

        importer.importFile(tempCsv, mapping);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM import_test_staging", Integer.class);
        assertThat(count).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Test 2 – ON CONFLICT DO NOTHING skips duplicate rows gracefully
    // -------------------------------------------------------------------------
    @Test
    @Order(2)
    void importCsv_duplicateRows_skippedGracefully() throws Exception {
        // Pre-insert one row
        jdbc.execute("INSERT INTO import_test_staging (product_name, description, price) " +
                     "VALUES ('Apple Phone','Existing row',79999)");

        // CSV re-submits the same product_name and adds a new one
        Files.writeString(tempCsv,
                "product_name,description,price\n" +
                "Apple Phone,Flagship smartphone,79999\n" +
                "Banana Phone,Mid-range phone,15000\n");

        DataImporter.CsvMapping mapping = new DataImporter.CsvMapping(
                "import_test_staging",
                new String[]{"product_name", "description", "price"},
                new String[]{"product_name", "description", "price"},
                null
        );

        // Must NOT throw; duplicate is silently skipped via ON CONFLICT DO NOTHING
        importer.importFile(tempCsv, mapping);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM import_test_staging", Integer.class);
        // 1 pre-inserted "Apple Phone" + 1 new "Banana Phone" = 2
        assertThat(count).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Test 3 – Malformed file failure is logged and isolated
    // -------------------------------------------------------------------------
    @Test
    @Order(3)
    void importCsv_malformedRow_rollsBackFileGracefully() throws Exception {
        // Row 2 has a non-numeric price for a NUMERIC column – causes the batch to fail and roll back safely
        Files.writeString(tempCsv,
                "product_name,description,price\n" +
                "Good Product,Valid row,999\n" +
                "Bad Product,Invalid price,NOT_A_NUMBER\n" +
                "Another Good,Valid again,1499\n");

        DataImporter.CsvMapping mapping = new DataImporter.CsvMapping(
                "import_test_staging",
                new String[]{"product_name", "description", "price"},
                new String[]{"product_name", "description", "price"},
                null
        );

        // Does not throw an uncaught exception out of the runner; rolls back this specific file cleanly
        importer.importFile(tempCsv, mapping);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM import_test_staging", Integer.class);
        // Clean rollback means 0 corrupt rows left in the table
        assertThat(count).isZero();
    }

    // -------------------------------------------------------------------------
    // Test 4 – Header-only CSV imports zero rows without crashing
    // -------------------------------------------------------------------------
    @Test
    @Order(4)
    void importCsv_emptyFile_importsZeroRows() throws Exception {
        Files.writeString(tempCsv, "product_name,description,price\n"); // header only

        DataImporter.CsvMapping mapping = new DataImporter.CsvMapping(
                "import_test_staging",
                new String[]{"product_name", "description", "price"},
                new String[]{"product_name", "description", "price"},
                null
        );

        importer.importFile(tempCsv, mapping);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM import_test_staging", Integer.class);
        assertThat(count).isZero();
    }
}
