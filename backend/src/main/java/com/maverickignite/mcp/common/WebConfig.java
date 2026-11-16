package com.maverickignite.mcp.common;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    // CORS is configured once for the whole app by the security config (holmes.config.SecurityConfig).

    /** Executor for long-running work (chat streams, MCP connects). */
    @Bean(destroyMethod = "close")
    ExecutorService nexusExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
