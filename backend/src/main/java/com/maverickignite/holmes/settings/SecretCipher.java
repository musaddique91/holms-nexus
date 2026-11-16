package com.maverickignite.holmes.settings;

import com.maverickignite.holmes.common.Texts;
import com.maverickignite.holmes.config.HolmesProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM for secrets kept in the database (LLM API keys). The key comes from
 * HOLMES_SETTINGS_KEY, or is derived from the JWT secret, so a database dump alone never
 * reveals an API key.
 */
@Component
public class SecretCipher {

  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public SecretCipher(HolmesProperties properties) {
    String material =
        Texts.firstNonBlank(
            properties.getSecurity().getSettingsKey(), properties.getSecurity().getJwtSecret());
    if (Texts.isBlank(material)) {
      throw new IllegalStateException("HOLMES_SETTINGS_KEY or HOLMES_JWT_SECRET must be set");
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(("holmes-settings:" + material).getBytes(StandardCharsets.UTF_8));
      this.key = new SecretKeySpec(digest, "AES");
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public String encrypt(String plain) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  /** Returns null when the value cannot be decrypted (e.g. the key material was rotated). */
  public String decrypt(String encoded) {
    if (Texts.isBlank(encoded)) {
      return null;
    }
    try {
      byte[] all = Base64.getDecoder().decode(encoded);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
      return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      return null;
    }
  }
}
