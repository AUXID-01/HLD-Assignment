package com.typeahead.search.component;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory buffer that accumulates search queries before they are
 * flushed in bulk to PostgreSQL by {@link BatchFlusher}.
 *
 * ─── KNOWN TRADE-OFF / DATA-LOSS RISK ──────────────────────────────────────
 * Any entries sitting in this buffer at the time of a JVM crash, OOM kill, or
 * ungraceful shutdown will be permanently lost — they will NEVER reach the DB.
 *
 * Root cause: this is pure in-memory state with no durability guarantee.
 *
 * Mitigations NOT implemented (out of scope for this phase):
 *   • Durable write-ahead log (WAL) / append-only file on disk
 *   • DB-backed transactional outbox pattern
 *   • External durable queue (Kafka, Redis Streams, RabbitMQ)
 *
 * Accepted consequence: search counts / last_searched_at values may be
 * slightly under-counted after a crash. Graceful shutdown (SIGTERM / Ctrl-C)
 * is safe because Spring's lifecycle will allow the @Scheduled flush to
 * complete, draining the buffer before the JVM exits.
 * ────────────────────────────────────────────────────────────────────────────
 *
 * Buffer data structure: {@code AtomicReference<ConcurrentHashMap<String, BufferedEntry>>}
 *
 * The AtomicReference holds the "live" map. HTTP threads call add() which
 * calls computeIfAbsent + increment on the live map. The flush thread calls
 * drain() which atomically swaps in a fresh empty map and returns the old one.
 * After the swap, incoming HTTP threads write to the NEW map, so no writes are
 * lost — they are simply deferred to the next flush cycle.
 */
@Component
public class SearchBuffer {

    // AtomicReference ensures the swap in drain() is a single atomic operation.
    private final AtomicReference<ConcurrentHashMap<String, BufferedEntry>> bufferRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    // ── Observability counters ──────────────────────────────────────────────
    private final AtomicLong totalSearchesReceived = new AtomicLong(0);
    private final AtomicLong totalDbWrites = new AtomicLong(0);

    /**
     * Records one search event. Called by every POST /search request thread.
     * Lock-free: ConcurrentHashMap + AtomicLong handle concurrent access.
     */
    public void add(String query) {
        totalSearchesReceived.incrementAndGet();
        long now = System.currentTimeMillis();
        bufferRef.get()
                .computeIfAbsent(query, k -> new BufferedEntry(now))
                .increment(now);
    }

    /**
     * Returns the current number of unique queries in the live buffer.
     * Used for size-threshold checks.
     */
    public int size() {
        return bufferRef.get().size();
    }

    /**
     * Atomically swaps the live buffer with a new empty map and returns the
     * old map for processing. After this call returns:
     *   • All subsequent add() calls go to the NEW empty map.
     *   • The returned map is owned exclusively by the flush thread — no other
     *     thread will write to it, so it can be iterated safely without locks.
     */
    public Map<String, BufferedEntry> drain() {
        return bufferRef.getAndSet(new ConcurrentHashMap<>());
    }

    // ── Observability accessors ─────────────────────────────────────────────

    public void recordDbWrites(long count) {
        totalDbWrites.addAndGet(count);
    }

    public long getTotalSearchesReceived() {
        return totalSearchesReceived.get();
    }

    public long getTotalDbWrites() {
        return totalDbWrites.get();
    }

    public int getCurrentBufferSize() {
        return bufferRef.get().size();
    }
}
