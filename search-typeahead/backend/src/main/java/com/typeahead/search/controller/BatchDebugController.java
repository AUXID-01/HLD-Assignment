package com.typeahead.search.controller;

import com.typeahead.search.component.SearchBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Debug endpoint to observe the write-reduction effectiveness of batch buffering.
 * Not required for production traffic — for demo/verification only.
 */
@RestController
public class BatchDebugController {

    private final SearchBuffer searchBuffer;

    public BatchDebugController(SearchBuffer searchBuffer) {
        this.searchBuffer = searchBuffer;
    }

    /**
     * GET /batch/debug
     *
     * Returns:
     *   currentBufferSize     — unique queries currently sitting unflushed in the buffer
     *   totalSearchesReceived — cumulative POST /search calls since startup
     *   totalDbWrites         — cumulative UPSERTs executed against PostgreSQL since startup
     *   writeReductionRatio   — searches-per-DB-write (higher = more savings)
     *   summary               — human-readable line for quick reading
     */
    @GetMapping("/batch/debug")
    public ResponseEntity<Map<String, Object>> debug() {
        long searches = searchBuffer.getTotalSearchesReceived();
        long writes = searchBuffer.getTotalDbWrites();
        int bufferSize = searchBuffer.getCurrentBufferSize();

        double ratio = (writes > 0) ? (double) searches / writes : 0.0;

        // LinkedHashMap preserves insertion order for a readable JSON response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("currentBufferSize", bufferSize);
        response.put("totalSearchesReceived", searches);
        response.put("totalDbWrites", writes);
        response.put("writeReductionRatio", String.format("%.2fx", ratio));
        response.put("summary", String.format(
                "%d searches in, %d DB writes out — %.2fx reduction",
                searches, writes, ratio));

        return ResponseEntity.ok(response);
    }
}
