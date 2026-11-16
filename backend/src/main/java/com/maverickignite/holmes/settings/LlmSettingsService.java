package com.maverickignite.holmes.settings;

import com.maverickignite.holmes.common.BadRequestException;
import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.mcp.llm.LlmProviderEntity;
import com.maverickignite.mcp.llm.LlmProviderRepository;
import com.maverickignite.mcp.llm.ProviderType;
import com.maverickignite.mcp.llm.ProvidersChangedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runtime LLM configuration for Intelligence.
 *
 * <p>In holms-nexus the model normally comes from the shared provider list (Models, table
 * llm_provider): the row here stores which provider and model to use, plus token/temperature
 * limits. With no provider chosen, the original standalone fields (provider, base URL, encrypted
 * key) still apply, and the key falls back to HOLMES_LLM_API_KEY.
 */
@Service
public class LlmSettingsService {

  public static final Set<String> PROVIDERS =
      Set.of("nvidia", "ollama", "anthropic", "openai", "azure", "gemini", "openrouter", "openai-compatible");

  /** Endpoints of the standalone providers when no base URL is set. */
  public static final Map<String, String> DEFAULT_URLS =
      Map.of(
          "nvidia", "https://integrate.api.nvidia.com/v1",
          "openai", "https://api.openai.com/v1",
          "openrouter", "https://openrouter.ai/api/v1",
          "gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
          "ollama", "http://host.docker.internal:11434/v1",
          "anthropic", "https://api.anthropic.com");

  private final LlmSettingsRepository repository;
  private final LlmProviderRepository providers;
  private final SecretCipher cipher;
  private final HolmesProperties.Llm defaults;
  private final AtomicReference<Effective> cache = new AtomicReference<>();
  /** Never reset: RoutingLlmClient rebuilds its client whenever this changes. */
  private final AtomicLong versions = new AtomicLong();

  /** Fully resolved settings, ready to build a client from. Never exposed over the API. */
  public record Effective(
      long version,
      String provider,
      String model,
      String fixModel,
      String baseUrl,
      String azureDeployment,
      String apiKey,
      String apiKeySource,
      String apiKeyHint,
      int maxTokens,
      String maxTokensParam,
      double temperature,
      Instant updatedAt,
      String updatedBy,
      UUID providerId,
      String providerName) {}

  public record Update(
      String provider,
      String model,
      String fixModel,
      String baseUrl,
      String azureDeployment,
      Integer maxTokens,
      String maxTokensParam,
      Double temperature,
      /* null = keep the saved key, "" = remove it, anything else = new key */
      String apiKey,
      /* shared provider id; null = keep, "" = stop using the shared list */
      String providerId) {}

  /** Where the connection details come from: a shared provider or the standalone fields. */
  record Connection(
      String provider,
      String baseUrl,
      String apiKey,
      String apiKeySource,
      String apiKeyHint,
      UUID providerId,
      String providerName) {}

  public LlmSettingsService(
      LlmSettingsRepository repository,
      LlmProviderRepository providers,
      SecretCipher cipher,
      HolmesProperties properties) {
    this.repository = repository;
    this.providers = providers;
    this.cipher = cipher;
    this.defaults = properties.getLlm();
  }

  public Effective current() {
    Effective e = cache.get();
    if (e == null) {
      e = load();
      cache.compareAndSet(null, e);
    }
    return e;
  }

  /** A provider's key, URL or models changed under Models: rebuild from the database next time. */
  @EventListener(ProvidersChangedEvent.class)
  public void onProvidersChanged() {
    cache.set(null);
  }

  /** Forget the cached settings (used after the one-time move to the shared provider list). */
  public void invalidate() {
    cache.set(null);
  }

  @Transactional
  public Effective update(Update u, String user) {
    LlmSettings s = repository.findById(LlmSettings.ID).orElseGet(this::newRow);
    Effective merged = merge(s, u);
    s.setProviderId(merged.providerId());
    s.setProvider(merged.provider());
    s.setModel(merged.model());
    s.setFixModel(trimOrNull(merged.fixModel()));
    s.setMaxTokens(merged.maxTokens());
    s.setMaxTokensParam(merged.maxTokensParam());
    s.setTemperature(BigDecimal.valueOf(merged.temperature()));
    if (merged.providerId() == null) {
      // Standalone fields only matter without a shared provider.
      s.setBaseUrl(merged.baseUrl() == null ? null : merged.baseUrl().trim().replaceFirst("/+$", ""));
      s.setAzureDeployment(trimOrNull(merged.azureDeployment()));
      if (u.apiKey() != null) {
        if (u.apiKey().isBlank()) {
          s.setApiKeyEncrypted(null);
          s.setApiKeyHint(null);
        } else {
          s.setApiKeyEncrypted(cipher.encrypt(u.apiKey().trim()));
          s.setApiKeyHint(hint(u.apiKey().trim()));
        }
      }
    }
    if (merged.providerId() != null) {
      offerModels(merged.providerId(), merged.model(), merged.fixModel());
    }
    s.setUpdatedAt(Instant.now());
    s.setUpdatedBy(user);
    repository.save(s);
    Effective fresh = toEffective(s);
    cache.set(fresh);
    return fresh;
  }

  /** Settings as they would be after applying {@code u}, without saving (for "Test connection"). */
  @Transactional(readOnly = true)
  public Effective preview(Update u) {
    return merge(repository.findById(LlmSettings.ID).orElseGet(this::newRow), u);
  }

  private Effective merge(LlmSettings s, Update u) {
    UUID providerId =
        u.providerId() == null ? s.getProviderId() : Texts.isBlank(u.providerId()) ? null : parseId(u.providerId());
    String model = Texts.firstNonBlank(u.model(), s.getModel());
    if (Texts.isBlank(model)) {
      throw new BadRequestException("Model is required");
    }
    int maxTokens = u.maxTokens() != null ? u.maxTokens() : s.getMaxTokens();
    if (maxTokens < 256 || maxTokens > 200_000) {
      throw new BadRequestException("Max tokens must be between 256 and 200000");
    }
    double temperature =
        u.temperature() != null ? u.temperature() : s.getTemperature() == null ? 0.1 : s.getTemperature().doubleValue();
    if (temperature < 0 || temperature > 2) {
      throw new BadRequestException("Temperature must be between 0 and 2");
    }

    Connection c;
    String azureDeployment;
    if (providerId != null) {
      LlmProviderEntity p =
          providers.findById(providerId).orElseThrow(() -> new BadRequestException("Unknown provider " + providerId));
      c = fromProvider(p);
      azureDeployment = null;
    } else {
      String provider = Texts.firstNonBlank(u.provider(), s.getProvider()).trim().toLowerCase(Locale.ROOT);
      if (!PROVIDERS.contains(provider)) {
        throw new BadRequestException("Unknown provider '" + provider + "'. Use one of " + PROVIDERS);
      }
      String savedKey = cipher.decrypt(s.getApiKeyEncrypted());
      String key = u.apiKey() == null ? savedKey : Texts.isBlank(u.apiKey()) ? null : u.apiKey().trim();
      String source = key != null ? "database" : !Texts.isBlank(defaults.getApiKey()) ? "environment" : "none";
      c =
          new Connection(
              provider,
              u.baseUrl() != null ? Texts.firstNonBlank(u.baseUrl(), null) : s.getBaseUrl(),
              key != null ? key : defaults.getApiKey(),
              source,
              u.apiKey() != null && !u.apiKey().isBlank() ? hint(u.apiKey().trim()) : s.getApiKeyHint(),
              null,
              null);
      azureDeployment =
          u.azureDeployment() != null ? Texts.firstNonBlank(u.azureDeployment(), null) : s.getAzureDeployment();
    }
    return new Effective(
        0,
        c.provider(),
        model.trim(),
        u.fixModel() != null ? Texts.firstNonBlank(u.fixModel(), null) : s.getFixModel(),
        c.baseUrl(),
        azureDeployment,
        c.apiKey(),
        c.apiKeySource(),
        c.apiKeyHint(),
        maxTokens,
        Texts.firstNonBlank(u.maxTokensParam(), s.getMaxTokensParam(), "max_tokens"),
        temperature,
        s.getUpdatedAt(),
        s.getUpdatedBy(),
        c.providerId(),
        c.providerName());
  }

  /** Models chosen here also appear in the provider's list, so MCP chat can pick them too. */
  private void offerModels(UUID providerId, String... models) {
    providers
        .findById(providerId)
        .ifPresent(
            p -> {
              java.util.List<String> list = new java.util.ArrayList<>(p.getModels() == null ? java.util.List.of() : p.getModels());
              boolean changed = false;
              for (String m : models) {
                if (!Texts.isBlank(m) && !list.contains(m.trim())) {
                  list.add(m.trim());
                  changed = true;
                }
              }
              if (changed) {
                p.setModels(list);
                providers.save(p);
              }
            });
  }

  private Effective load() {
    return toEffective(repository.findById(LlmSettings.ID).orElseGet(this::newRow));
  }

  private Effective toEffective(LlmSettings s) {
    Connection c = null;
    if (s.getProviderId() != null) {
      c = providers.findById(s.getProviderId()).map(LlmSettingsService::fromProvider).orElse(null);
    }
    if (c == null) {
      String saved = cipher.decrypt(s.getApiKeyEncrypted());
      c =
          new Connection(
              s.getProvider(),
              s.getBaseUrl(),
              saved != null ? saved : defaults.getApiKey(),
              saved != null ? "database" : !Texts.isBlank(defaults.getApiKey()) ? "environment" : "none",
              saved != null ? s.getApiKeyHint() : null,
              null,
              null);
    }
    return new Effective(
        versions.incrementAndGet(),
        c.provider(),
        s.getModel(),
        s.getFixModel(),
        c.baseUrl(),
        c.providerId() == null ? s.getAzureDeployment() : null,
        c.apiKey(),
        c.apiKeySource(),
        c.apiKeyHint(),
        s.getMaxTokens(),
        Texts.firstNonBlank(s.getMaxTokensParam(), "max_tokens"),
        s.getTemperature() == null ? 0.1 : s.getTemperature().doubleValue(),
        s.getUpdatedAt(),
        s.getUpdatedBy(),
        c.providerId(),
        c.providerName());
  }

  /**
   * Maps a shared provider onto the client Intelligence builds: Anthropic natively, everything
   * OpenAI-compatible (OpenAI, NVIDIA, OpenRouter, Gemini, vLLM…) through its base URL, and Ollama
   * through its OpenAI-compatible /v1 endpoint.
   */
  static Connection fromProvider(LlmProviderEntity p) {
    String base = Texts.isBlank(p.getBaseUrl()) ? null : p.getBaseUrl().trim().replaceFirst("/+$", "");
    String provider =
        switch (p.getType()) {
          case ANTHROPIC -> "anthropic";
          case OLLAMA -> "ollama";
          case OPENAI -> base == null ? "openai" : "openai-compatible";
        };
    if (p.getType() == ProviderType.OLLAMA) {
      String root = base == null ? "http://localhost:11434" : base;
      base = root.endsWith("/v1") ? root : root + "/v1";
    }
    String key = Texts.isBlank(p.getApiKey()) ? null : p.getApiKey();
    return new Connection(
        provider, base, key, key != null ? "database" : "none", key != null ? hint(key) : null, p.getId(), p.getName());
  }

  /** Used only if the Liquibase seed row is missing: falls back to holmes.llm.* settings. */
  private LlmSettings newRow() {
    LlmSettings s = new LlmSettings();
    s.setId(LlmSettings.ID);
    s.setProvider(defaults.getProvider());
    s.setModel(defaults.getModel());
    s.setFixModel(defaults.getFixModel());
    s.setBaseUrl(defaults.getBaseUrl());
    s.setAzureDeployment(defaults.getAzureDeployment());
    s.setMaxTokens(defaults.getMaxTokens());
    s.setMaxTokensParam(defaults.getMaxTokensParam());
    s.setTemperature(BigDecimal.valueOf(defaults.getTemperature()));
    return s;
  }

  private static UUID parseId(String id) {
    try {
      return UUID.fromString(id.trim());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid provider id");
    }
  }

  private static String trimOrNull(String s) {
    return Texts.isBlank(s) ? null : s.trim();
  }

  public static String hint(String key) {
    if (key.length() <= 10) {
      return "…" + key.substring(Math.max(0, key.length() - 2));
    }
    int dash = key.indexOf('-');
    String prefix = dash > 0 && dash <= 8 ? key.substring(0, dash + 1) : key.substring(0, 3);
    return prefix + "…" + key.substring(key.length() - 4);
  }

  /** Builds the HolmesProperties.Llm the concrete clients are constructed from. */
  public HolmesProperties.Llm toClientConfig(Effective e) {
    HolmesProperties.Llm c = new HolmesProperties.Llm();
    c.setProvider(e.provider());
    c.setModel(e.model());
    c.setFixModel(e.fixModel());
    c.setApiKey(e.apiKey());
    c.setBaseUrl(e.baseUrl());
    c.setAzureDeployment(e.azureDeployment());
    c.setAzureApiVersion(defaults.getAzureApiVersion());
    c.setMaxTokens(e.maxTokens());
    c.setMaxTokensParam(e.maxTokensParam());
    c.setTemperature(e.temperature());
    c.setTimeout(defaults.getTimeout());
    return c;
  }
}
