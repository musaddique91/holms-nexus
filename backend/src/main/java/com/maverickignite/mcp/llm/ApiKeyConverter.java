package com.maverickignite.mcp.llm;

import com.maverickignite.holmes.settings.SecretCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Stores provider API keys AES-GCM encrypted (same cipher and key as the MVXHolmes settings), so a
 * database dump alone never reveals a key. Values written before encryption existed are read back
 * as they are and encrypted on the next save.
 */
@Component
@Converter
public class ApiKeyConverter implements AttributeConverter<String, String> {

    private final SecretCipher cipher;

    public ApiKeyConverter(SecretCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String plain) {
        return plain == null || plain.isBlank() ? null : cipher.encrypt(plain);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        String plain = cipher.decrypt(stored);
        return plain != null ? plain : stored;
    }
}
