package com.maverickignite.holmes.llm;

import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.settings.LlmSettingsService;
import com.maverickignite.holmes.settings.LlmSettingsService.Effective;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * The LLM client the agents use. It follows the Settings page: when the provider, model or key
 * changes, the next call transparently uses a freshly built client — no restart needed.
 */
@Primary
@Component
public class RoutingLlmClient implements LlmClient {

  private record Built(long version, LlmClient client) {}

  private final LlmSettingsService settings;
  private final ObjectMapper json;
  private final RestClient.Builder builder;
  private final AtomicReference<Built> built = new AtomicReference<>();

  public RoutingLlmClient(LlmSettingsService settings, ObjectMapper json, RestClient.Builder builder) {
    this.settings = settings;
    this.json = json;
    this.builder = builder;
  }

  private LlmClient delegate() {
    Effective e = settings.current();
    Built b = built.get();
    if (b == null || b.version() != e.version()) {
      b = new Built(e.version(), LlmConfig.create(settings.toClientConfig(e), json, builder));
      built.set(b);
    }
    return b.client();
  }

  @Override
  public String provider() {
    return settings.current().provider();
  }

  @Override
  public String defaultModel() {
    return settings.current().model();
  }

  @Override
  public String fixModel() {
    Effective e = settings.current();
    return Texts.firstNonBlank(e.fixModel(), e.model());
  }

  @Override
  public int maxTokens() {
    return settings.current().maxTokens();
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    return delegate().complete(request);
  }
}
