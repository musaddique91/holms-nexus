package com.maverickignite.holmes.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.maverickignite.holmes.config.HolmesProperties;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

  private static SecretCipher cipher(String secret) {
    HolmesProperties p = new HolmesProperties();
    p.getSecurity().setJwtSecret(secret);
    return new SecretCipher(p);
  }

  @Test
  void roundTripsAndRandomisesCiphertext() {
    SecretCipher c = cipher("0123456789abcdef0123456789abcdef");
    String a = c.encrypt("demo-secret-value");
    String b = c.encrypt("demo-secret-value");
    assertThat(a).isNotEqualTo(b).doesNotContain("demo-secret");
    assertThat(c.decrypt(a)).isEqualTo("demo-secret-value");
  }

  @Test
  void wrongKeyOrGarbageDecryptsToNull() {
    String ct = cipher("0123456789abcdef0123456789abcdef").encrypt("x");
    assertThat(cipher("another-secret-another-secret-123").decrypt(ct)).isNull();
    assertThat(cipher("0123456789abcdef0123456789abcdef").decrypt("not-base64!")).isNull();
  }

  @Test
  void hintShowsOnlyPrefixAndLastFour() {
    assertThat(LlmSettingsService.hint("demo-abcdefghijklmnopWXYZ")).isEqualTo("demo-…WXYZ");
    assertThat(LlmSettingsService.hint("key-test-zzzzzzzz1234")).isEqualTo("key-…1234");
  }
}
