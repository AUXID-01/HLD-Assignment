package com.typeahead.search.component;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Implements a consistent hashing ring to distribute cache keys
 * across multiple Redis nodes using virtual nodes.
 *
 * Virtual node count (160 per physical node):
 * This is the same default used by memcached & Jedis clients.
 * At 160 virtual nodes per physical node with 3 nodes = 480 ring positions,
 * which provides ~5-10% standard deviation in key distribution —
 * good enough for most workloads while keeping the TreeMap small.
 */
@Component
public class ConsistentHashRouter {

    private static final int VIRTUAL_NODES_PER_PHYSICAL = 160;

    // Sorted ring: hash position -> node name
    private final SortedMap<Long, String> ring = new TreeMap<>();

    // Map of node name -> its StringRedisTemplate
    private final Map<String, StringRedisTemplate> redisTemplates;

    public ConsistentHashRouter(@Qualifier("redisNodeTemplates") Map<String, StringRedisTemplate> redisTemplates) {
        this.redisTemplates = redisTemplates;
        buildRing();
    }

    private void buildRing() {
        for (String nodeName : redisTemplates.keySet()) {
            for (int v = 0; v < VIRTUAL_NODES_PER_PHYSICAL; v++) {
                String virtualKey = nodeName + "#vnode" + v;
                long hash = md5Hash(virtualKey);
                ring.put(hash, nodeName);
            }
        }
    }

    /**
     * Deterministically routes the given cache key to a physical Redis node.
     */
    public String getNodeNameForKey(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Consistent hash ring is empty!");
        }
        long hash = md5Hash(key);
        SortedMap<Long, String> tail = ring.tailMap(hash);
        // Wrap around: if hash is beyond last entry, go to the first node
        Long nodeHash = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
        return ring.get(nodeHash);
    }

    /**
     * Returns the StringRedisTemplate for the node that owns the given key.
     */
    public StringRedisTemplate getTemplateForKey(String key) {
        String nodeName = getNodeNameForKey(key);
        return redisTemplates.get(nodeName);
    }

    /**
     * Exposes all available templates (used for multi-node invalidations).
     */
    public Map<String, StringRedisTemplate> getAllTemplates() {
        return redisTemplates;
    }

    /**
     * MD5-based hash producing a stable long value from a string key.
     * MD5 is chosen for its strong avalanche effect and uniform distribution.
     * Cryptographic security is NOT needed here; only stability and uniformity matter.
     */
    private long md5Hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes());
            // Use first 8 bytes to build a long
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            // MD5 is always available in Java — this will never happen
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
