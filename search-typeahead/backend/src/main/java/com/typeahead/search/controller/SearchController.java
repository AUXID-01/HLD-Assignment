package com.typeahead.search.controller;

import com.typeahead.search.component.ConsistentHashRouter;
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
    private final ConsistentHashRouter hashRouter;

    public SearchController(QueryRepository queryRepository, ConsistentHashRouter hashRouter) {
        this.queryRepository = queryRepository;
        this.hashRouter = hashRouter;
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, String>> search(@RequestBody Map<String, String> request) {
        String queryStr = request.get("query");

        if (queryStr == null || queryStr.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query cannot be missing or blank"));
        }

        queryStr = queryStr.trim().toLowerCase();

        // Performs a single atomic DB UPSERT
        queryRepository.upsertSearchQuery(queryStr, new Timestamp(System.currentTimeMillis()));

        // Cache Invalidation Strategy:
        // For each prefix of the query string, ask the ring which node owns it,
        // and delete that key from the correct node.
        // Each prefix can live on a DIFFERENT node, so we must route per-key.
        List<String> prefixes = new ArrayList<>();
        for (int i = 1; i <= queryStr.length(); i++) {
            String prefix = queryStr.substring(0, i);
            prefixes.add("suggest:basic:" + prefix);
            prefixes.add("suggest:trending:" + prefix);
        }

        // Group deletes by node to minimize round-trips
        Map<String, List<String>> keysByNode = new java.util.HashMap<>();
        for (String key : prefixes) {
            String nodeName = hashRouter.getNodeNameForKey(key);
            keysByNode.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(key);
        }

        Map<String, StringRedisTemplate> allTemplates = hashRouter.getAllTemplates();
        for (Map.Entry<String, List<String>> entry : keysByNode.entrySet()) {
            StringRedisTemplate template = allTemplates.get(entry.getKey());
            if (template != null) {
                template.delete(entry.getValue());
            }
        }

        return ResponseEntity.ok(Map.of("message", "Searched"));
    }
}
