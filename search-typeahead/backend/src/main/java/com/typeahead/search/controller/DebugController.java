package com.typeahead.search.controller;

import com.typeahead.search.component.ConsistentHashRouter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DebugController {

    private final ConsistentHashRouter hashRouter;

    public DebugController(ConsistentHashRouter hashRouter) {
        this.hashRouter = hashRouter;
    }

    @GetMapping("/cache/debug")
    public ResponseEntity<Map<String, String>> debug(
            @RequestParam(name = "prefix", required = false) String prefix) {

        if (prefix == null || prefix.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "prefix parameter is required"));
        }

        String normalizedPrefix = prefix.trim().toLowerCase();
        String redisKey = "suggest:" + normalizedPrefix;

        String nodeName = hashRouter.getNodeNameForKey(redisKey);
        StringRedisTemplate template = hashRouter.getTemplateForKey(redisKey);

        boolean isHit = Boolean.TRUE.equals(template.hasKey(redisKey));

        return ResponseEntity.ok(Map.of(
                "prefix", normalizedPrefix,
                "redis_key", redisKey,
                "node", nodeName,
                "status", isHit ? "HIT" : "MISS"
        ));
    }
}
