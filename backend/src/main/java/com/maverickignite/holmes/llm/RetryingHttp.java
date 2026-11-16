package com.maverickignite.holmes.llm;

import java.util.function.Supplier;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** Retries rate limits, overloads and network blips with exponential backoff. */
final class RetryingHttp {

  private static final int ATTEMPTS = 4;

  private RetryingHttp() {}

  static <T> T call(String provider, Supplier<T> call) {
    long backoffMs = 2_000;
    for (int attempt = 1; ; attempt++) {
      try {
        return call.get();
      } catch (RestClientResponseException e) {
        int status = e.getStatusCode().value();
        boolean retryable = status == 429 || status == 408 || status == 529 || status >= 500;
        if (!retryable || attempt >= ATTEMPTS) {
          String body = e.getResponseBodyAsString();
          throw new LlmException(
              provider + " returned " + status + ": " + body.substring(0, Math.min(500, body.length())),
              e);
        }
      } catch (ResourceAccessException e) {
        if (attempt >= ATTEMPTS) {
          throw new LlmException(provider + " unreachable: " + e.getMessage(), e);
        }
      }
      sleep(backoffMs);
      backoffMs *= 2;
    }
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new LlmException("Interrupted while backing off", e);
    }
  }
}
