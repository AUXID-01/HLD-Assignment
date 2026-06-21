package com.typeahead.search.component;

import com.typeahead.search.repository.QueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Periodically flushes the {@link SearchBuffer} to PostgreSQL and then
 * invalidates the relevant Redis cache keys.
 *
 * Two flush triggers exist:
 *   1. Time-based:  @Scheduled fires every batch.flush-interval-ms (default 10 s).
 *   2. Size-based:  record() checks the buffer size after each add and triggers
 *                   an immediate flush if it reaches batch.flush-size (default 500).
 *
 * Race between the two triggers:
 *   Both ultimately call the same synchronized flush() method. If size-based
 *   and time-based triggers fire simultaneously from different threads, one
 *   thread acquires the monitor first, drains the full buffer, and processes it.
 *   The second thread then acquires the monitor, calls drain() on an already-empty
 *   map, finds it empty, and returns immediately — no double-counting, no missed
 *   entries, no wasted DB round-trips.
 */
@Component
public class BatchFlusher {

    private static final Logger logger = LoggerFactory.getLogger(BatchFlusher.class);

    private final SearchBuffer searchBuffer;
    private final QueryRepository queryRepository;
    private final ConsistentHashRouter hashRouter;

    @Value("${batch.flush-size:500}")
    private int flushSize;

    public BatchFlusher(SearchBuffer searchBuffer,
                        QueryRepository queryRepository,
                        ConsistentHashRouter hashRouter) {
        this.searchBuffer = searchBuffer;
        this.queryRepository = queryRepository;
        this.hashRouter = hashRouter;
    }

    /**
     * Called by POST /search on every request.
     * Adds the query to the in-memory buffer, then triggers a size-based flush
     * if the buffer has reached the configured threshold.
     */
    public void record(String query) {
        searchBuffer.add(query);
        if (searchBuffer.size() >= flushSize) {
            logger.info("Buffer reached size threshold ({}). Triggering immediate flush.", flushSize);
            flush();
        }
    }

    /**
     * Time-based trigger: runs every batch.flush-interval-ms milliseconds
     * (measured from the END of the previous execution to avoid overlap).
     */
    @Scheduled(fixedDelayString = "${batch.flush-interval-ms:10000}")
    public void scheduledFlush() {
        flush();
    }

    /**
     * Core flush logic. Synchronized so that at most one flush runs at a time,
     * preventing size-based and time-based triggers from processing the same
     * entries concurrently.
     *
     * Steps:
     *   1. Atomically drain the buffer (swap with empty map).
     *   2. For each unique query, issue ONE UPSERT with the aggregated count.
     *   3. Invalidate both basic and trending Redis cache keys for all prefixes.
     *   4. Record metrics and emit a structured log line.
     */
    public synchronized void flush() {
        Map<String, BufferedEntry> snapshot = searchBuffer.drain();
        if (snapshot.isEmpty()) {
            return;
        }

        long startMs = System.currentTimeMillis();
        long totalIncrements = snapshot.values().stream()
                .mapToLong(BufferedEntry::getCount)
                .sum();

        int successfulWrites = 0;

        for (Map.Entry<String, BufferedEntry> entry : snapshot.entrySet()) {
            String query = entry.getKey();
            BufferedEntry buffered = entry.getValue();

            try {
                // ONE UPSERT per unique query with the aggregated count delta
                queryRepository.batchUpsertSearchQuery(
                        query,
                        buffered.getCount(),
                        new Timestamp(buffered.getLatestTimestampMs())
                );
                successfulWrites++;

                // ── Cache Invalidation ────────────────────────────────────────────
                // Invalidate suggest:basic:<prefix> and suggest:trending:<prefix>
                // for every prefix of the query, routed to the correct Redis node.
                List<String> cacheKeys = new ArrayList<>();
                for (int i = 1; i <= query.length(); i++) {
                    String prefix = query.substring(0, i);
                    cacheKeys.add("suggest:basic:" + prefix);
                    cacheKeys.add("suggest:trending:" + prefix);
                }

                // Group by owning node to minimize Redis round-trips
                Map<String, List<String>> keysByNode = new HashMap<>();
                for (String cacheKey : cacheKeys) {
                    String nodeName = hashRouter.getNodeNameForKey(cacheKey);
                    keysByNode.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(cacheKey);
                }

                Map<String, StringRedisTemplate> allTemplates = hashRouter.getAllTemplates();
                for (Map.Entry<String, List<String>> nodeEntry : keysByNode.entrySet()) {
                    StringRedisTemplate template = allTemplates.get(nodeEntry.getKey());
                    if (template != null) {
                        try {
                            template.delete(nodeEntry.getValue());
                        } catch (Exception e) {
                            // Cache invalidation failure is non-fatal; stale data expires via TTL
                            logger.warn("Cache invalidation failed for node '{}' (non-fatal): {}",
                                    nodeEntry.getKey(), e.getMessage());
                        }
                    }
                }

            } catch (Exception e) {
                logger.error("Failed to flush query '{}' to DB — {} counts may be lost: {}",
                        query, buffered.getCount(), e.getMessage());
            }
        }

        searchBuffer.recordDbWrites(successfulWrites);
        long elapsed = System.currentTimeMillis() - startMs;
        logger.info("Flushed {} unique queries ({} total increments) to DB in {}ms",
                snapshot.size(), totalIncrements, elapsed);
    }
}
