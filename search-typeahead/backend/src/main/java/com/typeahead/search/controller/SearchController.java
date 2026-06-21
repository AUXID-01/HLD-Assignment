package com.typeahead.search.controller;

import com.typeahead.search.component.BatchFlusher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Handles POST /search.
 *
 * Phase 9 change: the synchronous DB UPSERT + cache invalidation that ran
 * inline on every request has been replaced by a single call to
 * BatchFlusher.record(). The HTTP response is returned immediately without
 * waiting for the DB write, which is now deferred to the next batch flush.
 *
 * The /search response contract is UNCHANGED from the user's perspective:
 *   • Still returns { "message": "Searched" } with HTTP 200.
 *   • Still returns 400 for blank/missing query.
 */
@RestController
public class SearchController {

    private final BatchFlusher batchFlusher;

    public SearchController(BatchFlusher batchFlusher) {
        this.batchFlusher = batchFlusher;
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, String>> search(@RequestBody Map<String, String> request) {
        String queryStr = request.get("query");

        if (queryStr == null || queryStr.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query cannot be missing or blank"));
        }

        queryStr = queryStr.trim().toLowerCase();

        // Buffer the search event. The HTTP thread returns immediately;
        // the DB UPSERT and cache invalidation happen asynchronously on flush.
        batchFlusher.record(queryStr);

        return ResponseEntity.ok(Map.of("message", "Searched"));
    }
}
