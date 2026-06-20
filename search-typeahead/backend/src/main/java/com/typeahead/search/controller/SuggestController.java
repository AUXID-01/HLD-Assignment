package com.typeahead.search.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typeahead.search.component.ConsistentHashRouter;
import com.typeahead.search.dto.SuggestResponse;
import com.typeahead.search.repository.QueryRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class SuggestController {

    private final QueryRepository queryRepository;
    private final ConsistentHashRouter hashRouter;
    private final ObjectMapper objectMapper;

    public SuggestController(QueryRepository queryRepository, ConsistentHashRouter hashRouter, ObjectMapper objectMapper) {
        this.queryRepository = queryRepository;
        this.hashRouter = hashRouter;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/suggest")
    public ResponseEntity<List<SuggestResponse>> suggest(@RequestParam(name = "q", required = false) String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return ResponseEntity.ok().header("X-Cache", "MISS").body(Collections.emptyList());
        }

        String normalizedPrefix = prefix.trim().toLowerCase();
        String redisKey = "suggest:" + normalizedPrefix;

        // Route to the deterministically correct node via the consistent hash ring
        StringRedisTemplate template = hashRouter.getTemplateForKey(redisKey);
        String nodeName = hashRouter.getNodeNameForKey(redisKey);

        try {
            String cachedData = template.opsForValue().get(redisKey);
            if (cachedData != null) {
                List<SuggestResponse> responses = objectMapper.readValue(cachedData, new TypeReference<>() {});
                return ResponseEntity.ok()
                        .header("X-Cache", "HIT")
                        .header("X-Cache-Node", nodeName)
                        .body(responses);
            }
        } catch (Exception e) {
            // Ignore cache read errors and fallback to DB
        }

        List<SuggestResponse> dbResults = queryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(normalizedPrefix)
                .stream()
                .map(p -> new SuggestResponse(p.getQuery(), p.getCount()))
                .collect(Collectors.toList());

        try {
            String json = objectMapper.writeValueAsString(dbResults);
            template.opsForValue().set(redisKey, json, Duration.ofSeconds(60));
        } catch (Exception e) {
            // Ignore cache write errors
        }

        return ResponseEntity.ok()
                .header("X-Cache", "MISS")
                .header("X-Cache-Node", nodeName)
                .body(dbResults);
    }
}
