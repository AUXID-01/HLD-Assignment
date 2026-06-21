package com.typeahead.search.component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds aggregated state for a single query term sitting in the in-memory batch buffer.
 *
 * Thread-safety contract:
 *   - count is an AtomicLong: concurrent increments from multiple HTTP threads are safe.
 *   - latestTimestampMs is volatile: writes from any thread are immediately visible to the
 *     flush thread. We accept a small race on the CAS-free update (two threads may both
 *     read the same old value and one "wins" the write) — for a timestamp this is harmless
 *     because both candidate values are valid recent timestamps.
 */
public class BufferedEntry {

    private final AtomicLong count = new AtomicLong(0);
    private volatile long latestTimestampMs;

    public BufferedEntry(long initialTimestampMs) {
        this.latestTimestampMs = initialTimestampMs;
    }

    /**
     * Thread-safe increment. Called by every HTTP request thread that observes this query.
     */
    public void increment(long timestampMs) {
        count.incrementAndGet();
        // Keep the most recent timestamp seen; harmless if two threads race here —
        // both candidate values are recent enough to be correct for last_searched_at.
        if (timestampMs > this.latestTimestampMs) {
            this.latestTimestampMs = timestampMs;
        }
    }

    public long getCount() {
        return count.get();
    }

    public long getLatestTimestampMs() {
        return latestTimestampMs;
    }
}
