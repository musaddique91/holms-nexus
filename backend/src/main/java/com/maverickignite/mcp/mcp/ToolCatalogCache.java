package com.maverickignite.mcp.mcp;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.maverickignite.mcp.common.Json;
import com.maverickignite.mcp.common.NexusProperties;

import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.core.type.TypeReference;

/**
 * Redis cache of each server's last-known tool catalog. Lets the UI show tools for servers that are
 * currently offline / still connecting, and survives backend restarts.
 */
@Component
public class ToolCatalogCache {

    private static final Logger log = LoggerFactory.getLogger(ToolCatalogCache.class);

    private final StringRedisTemplate redis;

    private final NexusProperties properties;

    public ToolCatalogCache(StringRedisTemplate redis, NexusProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    private String key(UUID serverId) {
        return properties.redisPrefix() + "mcp:tools:" + serverId;
    }

    public void put(UUID serverId, List<McpSchema.Tool> tools) {
        try {
            redis.opsForValue().set(key(serverId), Json.write(tools), properties.toolsCacheTtl());
        }
        catch (Exception ex) {
            log.debug("Redis unavailable, skipping tool cache write: {}", ex.getMessage());
        }
    }

    public List<McpSchema.Tool> get(UUID serverId) {
        try {
            String json = redis.opsForValue().get(key(serverId));
            if (json == null) {
                return List.of();
            }
            return Json.read(json, new TypeReference<List<McpSchema.Tool>>() {
            });
        }
        catch (Exception ex) {
            log.debug("Redis unavailable, skipping tool cache read: {}", ex.getMessage());
            return List.of();
        }
    }

    public void evict(UUID serverId) {
        try {
            redis.delete(key(serverId));
        }
        catch (Exception ex) {
            log.debug("Redis unavailable, skipping tool cache evict: {}", ex.getMessage());
        }
    }
}
