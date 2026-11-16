package com.maverickignite.holmes.settings;

import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import com.maverickignite.mcp.llm.LlmProviderEntity;
import com.maverickignite.mcp.llm.LlmProviderRepository;
import com.maverickignite.mcp.llm.ProviderType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One-time move to the shared Models list (holms-nexus). If Intelligence still uses its standalone
 * settings, they become a provider in the shared list (same URL, same key, now encrypted by the
 * shared converter) and Intelligence is pointed at it, so the key is managed in one place. Runs on
 * every start but does nothing once a provider is chosen. Azure keeps the standalone settings:
 * the shared list has no Azure deployment field.
 */
@Slf4j
@Component
@Order(0)
class SharedModelMigration implements ApplicationRunner {

  private static final Map<String, String> NAMES =
      Map.of(
          "nvidia", "NVIDIA NIM",
          "openai", "OpenAI",
          "anthropic", "Anthropic",
          "gemini", "Google Gemini",
          "openrouter", "OpenRouter",
          "ollama", "Ollama",
          "openai-compatible", "Custom (OpenAI-compatible)");

  private final LlmSettingsRepository settings;
  private final LlmProviderRepository providers;
  private final LlmSettingsService service;
  private final SecretCipher cipher;
  private final HolmesProperties.Llm defaults;
  private final TransactionTemplate tx;

  SharedModelMigration(
      LlmSettingsRepository settings,
      LlmProviderRepository providers,
      LlmSettingsService service,
      SecretCipher cipher,
      HolmesProperties properties,
      TransactionTemplate tx) {
    this.settings = settings;
    this.providers = providers;
    this.service = service;
    this.cipher = cipher;
    this.defaults = properties.getLlm();
    this.tx = tx;
  }

  @Override
  public void run(ApplicationArguments args) {
    Boolean moved = tx.execute(status -> migrate());
    if (Boolean.TRUE.equals(moved)) {
      service.invalidate();
    }
  }

  private boolean migrate() {
    LlmSettings s = settings.findById(LlmSettings.ID).orElse(null);
    if (s == null || s.getProviderId() != null || !NAMES.containsKey(s.getProvider())) {
      return false;
    }
    String key = Texts.firstNonBlank(cipher.decrypt(s.getApiKeyEncrypted()), defaults.getApiKey());
    String url = Texts.firstNonBlank(s.getBaseUrl(), LlmSettingsService.DEFAULT_URLS.get(s.getProvider()));

    LlmProviderEntity p = new LlmProviderEntity();
    p.setName(NAMES.get(s.getProvider()));
    switch (s.getProvider()) {
      case "anthropic" -> {
        p.setType(ProviderType.ANTHROPIC);
        p.setBaseUrl(Texts.isBlank(s.getBaseUrl()) ? null : s.getBaseUrl());
      }
      case "ollama" -> {
        // The shared list talks to Ollama's native API; Intelligence adds /v1 itself.
        p.setType(ProviderType.OLLAMA);
        p.setBaseUrl(url == null ? null : url.replaceFirst("/v1/?$", ""));
      }
      case "openai" -> {
        p.setType(ProviderType.OPENAI);
        p.setBaseUrl(Texts.isBlank(s.getBaseUrl()) ? null : s.getBaseUrl());
      }
      default -> {
        p.setType(ProviderType.OPENAI);
        p.setBaseUrl(url);
      }
    }
    p.setApiKey(key);
    p.setDefaultModel(s.getModel());
    List<String> models = new ArrayList<>(List.of(s.getModel()));
    if (!Texts.isBlank(s.getFixModel()) && !models.contains(s.getFixModel())) {
      models.add(s.getFixModel());
    }
    p.setModels(models);
    p.setEnabled(true);
    LlmProviderEntity saved = providers.save(p);

    s.setProviderId(saved.getId());
    settings.save(s);
    log.info(
        "Intelligence now uses '{}' ({}) from the shared Models list; key {}",
        saved.getName(), s.getModel(), key == null ? "not set yet" : "moved, encrypted");
    return true;
  }
}
