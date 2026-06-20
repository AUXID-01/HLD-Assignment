package com.typeahead.search.component;

import com.typeahead.search.repository.QueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    private final QueryRepository queryRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.dataset.path:classpath:data/queries_aggregated.csv}")
    private Resource datasetResource;

    public DataLoader(QueryRepository queryRepository, JdbcTemplate jdbcTemplate) {
        this.queryRepository = queryRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        if (queryRepository.count() > 0) {
            logger.info("Table 'queries' already has data. Skipping data loading.");
            return;
        }

        logger.info("Starting data load from {}", datasetResource.getFilename());
        
        long startTime = System.currentTimeMillis();
        int batchSize = 10000;
        int totalLoaded = 0;
        
        String sql = "INSERT INTO queries (query, count, last_searched_at) VALUES (?, ?, ?) ON CONFLICT (query) DO NOTHING";
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(datasetResource.getInputStream()))) {
            String line = reader.readLine(); // Skip header
            
            List<Object[]> batch = new ArrayList<>();
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                int lastComma = line.lastIndexOf(',');
                int secondLastComma = line.lastIndexOf(',', lastComma - 1);
                
                if (lastComma == -1 || secondLastComma == -1) {
                    continue; // Skip malformed lines
                }
                
                String queryStr = line.substring(0, secondLastComma);
                if (queryStr.startsWith("\"") && queryStr.endsWith("\"") && queryStr.length() >= 2) {
                    queryStr = queryStr.substring(1, queryStr.length() - 1);
                }
                
                String countStr = line.substring(secondLastComma + 1, lastComma);
                String timestampStr = line.substring(lastComma + 1);
                
                try {
                    Long count = Long.parseLong(countStr);
                    Timestamp timestamp = Timestamp.valueOf(LocalDateTime.parse(timestampStr, formatter));
                    
                    // Cap query length to avoid constraint violations if there's any
                    if (queryStr.length() > 255) {
                        queryStr = queryStr.substring(0, 255);
                    }

                    batch.add(new Object[]{queryStr, count, timestamp});
                    
                    if (batch.size() >= batchSize) {
                        jdbcTemplate.batchUpdate(sql, batch);
                        totalLoaded += batch.size();
                        batch.clear();
                        
                        if (totalLoaded % 100000 == 0) {
                            logger.info("Loaded {} rows...", totalLoaded);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse line: " + line, e);
                }
            }
            
            if (!batch.isEmpty()) {
                jdbcTemplate.batchUpdate(sql, batch);
                totalLoaded += batch.size();
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Data load complete! Inserted {} rows in {} ms.", totalLoaded, duration);
        }
    }
}
