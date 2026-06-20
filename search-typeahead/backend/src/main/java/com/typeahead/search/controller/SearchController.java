package com.typeahead.search.controller;

import com.typeahead.search.repository.QueryRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class SearchController {

    private final QueryRepository queryRepository;
    private final StringRedisTemplate redisTemplate;

    public SearchController(QueryRepository queryRepository, StringRedisTemplate redisTemplate) {
        this.queryRepository = queryRepository;
        this.redisTemplate = redisTemplate;
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

        // Cache Invalidation Strategy:
        // We delete all possible prefix keys for this specific query up to its full length.
        List<String> keysToDelete = new ArrayList<>();
        for (int i = 1; i <= queryStr.length(); i++) {
            keysToDelete.add("suggest:" + queryStr.substring(0, i));
        }
        redisTemplate.delete(keysToDelete);

        return ResponseEntity.ok(Map.of("message", "Searched"));
    }
}
