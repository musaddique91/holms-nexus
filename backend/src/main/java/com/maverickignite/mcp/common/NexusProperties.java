package com.maverickignite.mcp.common;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus")
public record NexusProperties(
        List<String> corsOrigins,
        String redisPrefix,
        Duration toolsCacheTtl,
        int historyLimit,
        Bootstrap bootstrap) {

    public record Bootstrap(String anthropicApiKey, String openaiApiKey, String ollamaBaseUrl) {
    }
}
