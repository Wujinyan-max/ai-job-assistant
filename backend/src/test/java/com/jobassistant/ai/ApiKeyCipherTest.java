package com.jobassistant.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyCipherTest {

    @Test
    void encryptsWithoutPersistingPlaintextAndDecrypts() {
        ApiKeyCipher cipher = new ApiKeyCipher("test-secret-that-is-long-enough");
        String first = cipher.encrypt("sk-secret-value");
        String second = cipher.encrypt("sk-secret-value");

        assertThat(first).doesNotContain("sk-secret-value").startsWith("v1:");
        assertThat(second).isNotEqualTo(first);
        assertThat(cipher.decrypt(first)).isEqualTo("sk-secret-value");
        assertThat(cipher.mask("sk-secret-value")).isEqualTo("sk-s••••••alue");
    }
}
