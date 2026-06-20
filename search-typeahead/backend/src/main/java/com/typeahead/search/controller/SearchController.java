package com.typeahead.search.controller;

import com.typeahead.search.repository.QueryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.Map;

@RestController
public class SearchController {

    private final QueryRepository queryRepository;

    public SearchController(QueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, String>> search(@RequestBody Map<String, String> request) {
        String queryStr = request.get("query");
        
        if (queryStr == null || queryStr.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query cannot be missing or blank"));
        }

        // Case-insensitive exact match logic: 
        // We trim and lowercase the query so it's safely and consistently stored in the DB.
        queryStr = queryStr.trim().toLowerCase();

        // Performs a single atomic DB operation via native UPSERT.
        queryRepository.upsertSearchQuery(queryStr, new Timestamp(System.currentTimeMillis()));

        return ResponseEntity.ok(Map.of("message", "Searched"));
    }
}
