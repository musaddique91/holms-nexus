package com.maverickignite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MVX Nexus: two applications behind one login.
 * <ul>
 *   <li>{@code com.maverickignite.holmes} – MVXHolmes, the AI SRE (incidents, root cause, fix PRs, alerts)
 *   <li>{@code com.maverickignite.mcp} – MCP, chat with your data through MCP servers
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class NexusApplication {
  public static void main(String[] args) {
    SpringApplication.run(NexusApplication.class, args);
  }
}
