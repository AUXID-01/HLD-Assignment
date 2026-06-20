package com.typeahead.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class RedisConfig {

    private final RedisProperties redisProperties;

    public RedisConfig(RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    /**
     * Returns a named map of node name -> StringRedisTemplate,
     * one template per physical Redis node.
     * Named "redisNodeTemplates" to avoid conflicting with Spring's
     * auto-configured default StringRedisTemplate bean.
     */
    @Bean(name = "redisNodeTemplates")
    public Map<String, StringRedisTemplate> redisNodeTemplates() {
        Map<String, StringRedisTemplate> templates = new LinkedHashMap<>();

        for (RedisProperties.NodeConfig nodeConfig : redisProperties.getNodes()) {
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                    nodeConfig.getHost(), nodeConfig.getPort());
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.afterPropertiesSet();
            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();
            templates.put(nodeConfig.getName(), template);
        }

        return templates;
    }
}
