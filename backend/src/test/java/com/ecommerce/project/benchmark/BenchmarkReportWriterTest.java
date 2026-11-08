package com.ecommerce.project.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("test")
public class BenchmarkReportWriterTest {

    @Test
    void writeBenchmarkReport() throws IOException {
        Map<String, Map<String, Object>> metrics = BenchmarkReport.snapshot();
        
        List<Map<String, Object>> reportData = new ArrayList<>(metrics.values());
        
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(reportData);
        
        String backendTarget = System.getProperty("user.dir") + "/target/benchmark-report.json";
        String frontendPublic = System.getProperty("user.dir") + "/../frontend/public/benchmark-report.json";
        
        writeToFile(backendTarget, json);
        writeToFile(frontendPublic, json);
        
        System.out.println("Benchmark report written to:");
        System.out.println("  " + backendTarget);
        System.out.println("  " + frontendPublic);
    }
    
    private void writeToFile(String path, String content) throws IOException {
        File file = new File(path);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
}
