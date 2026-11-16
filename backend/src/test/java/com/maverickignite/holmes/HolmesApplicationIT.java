package com.maverickignite.holmes;

import static org.assertj.core.api.Assertions.assertThat;

import com.maverickignite.holmes.catalog.ServiceCatalog;
import com.maverickignite.holmes.incident.IncidentRepository;
import com.maverickignite.holmes.incident.IncidentService;
import com.maverickignite.holmes.incident.IncidentStatus;
import com.maverickignite.holmes.incident.IncidentStore;
import com.maverickignite.holmes.ingestion.ErrorEvent;
import com.maverickignite.holmes.ingestion.IngestionService;
import com.maverickignite.holmes.llm.LlmClient;
import com.maverickignite.holmes.settings.LlmSettingsService;
import com.maverickignite.holmes.toolset.ToolRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.maverickignite.mcp.llm.LlmDtos.ProviderRequest;
import com.maverickignite.mcp.llm.LlmProviderService;
import com.maverickignite.mcp.llm.ProviderType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Boots the whole application on a real PostgreSQL (Liquibase schema, SQL upserts, wiring). */
@SpringBootTest(
    properties = {
      "holmes.ingestion.kafka.enabled=false",
      "holmes.investigation.enabled=false",
      "holmes.security.admin-password=test-password",
      "holmes.security.jwt-secret=0123456789abcdef0123456789abcdef",
      "holmes.llm.api-key=test-key",
      "holmes.toolsets.cli.docker.enabled=true",
      "holmes.seed-services[0].name=orders-api",
      "holmes.seed-services[0].aliases[0]=orders-backend",
      "holmes.seed-services[1].name=payments-api",
      "holmes.seed-services[2].name=billing-api",
      "spring.kafka.listener.auto-startup=false"
    })
@Testcontainers(disabledWithoutDocker = true)
class HolmesApplicationIT {

  @TestConfiguration(proxyBeanMethods = false)
  static class Containers {
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
      return new PostgreSQLContainer("postgres:17-alpine");
    }
  }

  @Autowired IngestionService ingestion;
  @Autowired IncidentRepository incidents;
  @Autowired IncidentStore store;
  @Autowired IncidentService incidentService;
  @Autowired ServiceCatalog catalog;
  @Autowired ToolRegistry tools;
  @Autowired LlmSettingsService llmSettings;
  @Autowired LlmClient llm;
  @Autowired LlmProviderService providers;
  @Autowired JdbcTemplate jdbc;

  @Test
  void deduplicatesOccurrencesAndReopensResolvedIncidents() {
    ErrorEvent event =
        new ErrorEvent(
            "orders-backend", "prod", "ERROR", "Order 1 failed",
            "java.lang.IllegalStateException: boom\n\tat com.mshiba.backend.Order.place(Order.java:10)",
            "c.m.Order", "vm-1", Instant.now(), "test");

    Long first = ingestion.ingest(event);
    Long second = ingestion.ingest(event);
    assertThat(second).isEqualTo(first);

    var incident = incidents.findById(first).orElseThrow();
    assertThat(incident.getServiceName()).isEqualTo("orders-api"); // resolved via alias
    assertThat(incident.getOccurrenceCount()).isEqualTo(2);
    assertThat(incident.getStatus()).isEqualTo(IncidentStatus.NEW);

    assertThat(store.claimNew(5)).containsExactly(first);
    assertThat(store.claimNew(5)).isEmpty();

    incidentService.transition(first, IncidentStatus.RESOLVED);
    ingestion.ingest(event);
    assertThat(incidents.findById(first).orElseThrow().getStatus()).isEqualTo(IncidentStatus.NEW);
  }

  @Test
  void filtersNoiseAndNonErrors() {
    assertThat(ingestion.ingest(new ErrorEvent("x", null, "INFO", "started", null, null, null, Instant.now(), "t"))).isNull();
    assertThat(ingestion.ingest(new ErrorEvent("x", null, "ERROR", "java.io.IOException: Broken pipe", null, null, null, Instant.now(), "t"))).isNull();
  }

  @Test
  void seedsServicesAndLoadsToolsets() {
    assertThat(catalog.findAll()).extracting("name").contains("orders-api", "payments-api", "billing-api");
    List<String> names = tools.activeTools().stream().map(t -> t.name()).toList();
    assertThat(names).contains("docker_logs", "docker_ps").doesNotContain("kubectl_get");
  }

  @Test
  void holmesModelMovesToTheSharedProviderListWithTheKeyEncrypted() {
    // On start-up the seeded NVIDIA settings became a provider in the shared Models list.
    var seeded = llmSettings.current();
    assertThat(seeded.providerName()).isEqualTo("NVIDIA NIM");
    assertThat(seeded.providerId()).isNotNull();
    assertThat(seeded.provider()).isEqualTo("openai-compatible");
    assertThat(seeded.baseUrl()).isEqualTo("https://integrate.api.nvidia.com/v1");
    assertThat(seeded.model()).isEqualTo("mistralai/mistral-large-4-1t-a52b");
    assertThat(seeded.apiKey()).isEqualTo("test-key"); // was holmes.llm.api-key
    assertThat(seeded.apiKeySource()).isEqualTo("database");
    assertThat(llm.provider()).isEqualTo("openai-compatible");
    // Stored encrypted: the raw column never contains the key.
    String raw = jdbc.queryForObject("select api_key from llm_provider where id = ?", String.class, seeded.providerId());
    assertThat(raw).isNotBlank().doesNotContain("test-key");

    // Editing the provider under Models reaches MVXHolmes without a restart.
    providers.update(
        seeded.providerId(),
        new ProviderRequest("NVIDIA NIM", ProviderType.OPENAI, "demo-new-key-1234", "https://integrate.api.nvidia.com/v1",
            "mistralai/mistral-large-4-1t-a52b", List.of("mistralai/mistral-large-4-1t-a52b"), true));
    assertThat(llmSettings.current().apiKey()).isEqualTo("demo-new-key-1234");
    assertThat(llmSettings.current().version()).isGreaterThan(seeded.version());
  }

  @Test
  void standaloneSettingsStillWorkWhenNoSharedProviderIsChosen() {
    UUID shared = llmSettings.current().providerId();
    var saved =
        llmSettings.update(
            new LlmSettingsService.Update("ollama", "gpt-oss:120b-cloud", null, "http://ollama:11434/v1",
                null, 8192, null, null, "k-1234567890abcd", ""),
            "test");
    assertThat(saved.providerId()).isNull();
    assertThat(saved.apiKeySource()).isEqualTo("database");
    assertThat(saved.apiKeyHint()).isEqualTo("k-…abcd");
    assertThat(llm.provider()).isEqualTo("ollama");
    assertThat(llm.defaultModel()).isEqualTo("gpt-oss:120b-cloud");

    // omitted optional fields keep their saved values; "" clears them
    assertThat(llmSettings.update(new LlmSettingsService.Update(null, null, null, null, null, null, null, null, null, null), "t").baseUrl())
        .isEqualTo("http://ollama:11434/v1");
    assertThat(llmSettings.update(new LlmSettingsService.Update(null, null, null, "", null, null, null, null, null, null), "t").baseUrl())
        .isNull();
    // null key keeps the saved one; "" removes it (then the environment key applies)
    assertThat(llmSettings.update(new LlmSettingsService.Update(null, null, null, null, null, null, null, null, null, null), "t").apiKey())
        .isEqualTo("k-1234567890abcd");
    assertThat(llmSettings.update(new LlmSettingsService.Update(null, null, null, null, null, null, null, null, "", null), "t").apiKeySource())
        .isEqualTo("environment");

    // And back to the shared provider.
    var back = llmSettings.update(
        new LlmSettingsService.Update(null, "mistralai/mistral-large-4-1t-a52b", null, null, null, null, null, null, null, shared.toString()),
        "t");
    assertThat(back.providerId()).isEqualTo(shared);
  }
}
