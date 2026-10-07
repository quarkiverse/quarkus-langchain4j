package io.quarkiverse.langchain4j.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensitiveHeaderMaskerTest {

    @Test
    void shouldDetectSensitiveHeaderNames() {
        assertTrue(SensitiveHeaderMasker.isSensitive("Authorization"));
        assertTrue(SensitiveHeaderMasker.isSensitive("x-api-key"));
        assertTrue(SensitiveHeaderMasker.isSensitive("X-Gateway-Token"));
        assertTrue(SensitiveHeaderMasker.isSensitive("X-API-Gateway-Key"));
        assertTrue(SensitiveHeaderMasker.isSensitive("Set-Cookie"));
        assertTrue(SensitiveHeaderMasker.isSensitive("authorization"));
    }

    @Test
    void shouldNotDetectRegularHeaderNames() {
        assertFalse(SensitiveHeaderMasker.isSensitive("Content-Type"));
        assertFalse(SensitiveHeaderMasker.isSensitive("Accept"));
        assertFalse(SensitiveHeaderMasker.isSensitive("Keep-Alive"));
        assertFalse(SensitiveHeaderMasker.isSensitive(null));
    }

    @Test
    void shouldNotDetectRateLimitHeaderNames() {
        // These names match the "token"/"key" keywords but are rate-limit metadata, not credentials
        assertFalse(SensitiveHeaderMasker.isSensitive("x-ratelimit-remaining-tokens"));
        assertFalse(SensitiveHeaderMasker.isSensitive("anthropic-ratelimit-output-tokens-remaining"));
        assertFalse(SensitiveHeaderMasker.isSensitive("X-RateLimit-Remaining"));
        assertFalse(SensitiveHeaderMasker.isSensitive("RateLimit-Reset"));
    }

    @Test
    void shouldMaskValueKeepingEdges() {
        assertEquals("Beare...00", SensitiveHeaderMasker.mask("Bearer sk-fake-api-key-0000"));
        assertEquals("tok-s...11", SensitiveHeaderMasker.mask("tok-secret-1111"));
    }

    @Test
    void shouldFullyMaskShortValues() {
        assertEquals("...", SensitiveHeaderMasker.mask("abc"));
        assertEquals("...", SensitiveHeaderMasker.mask("secret"));
    }

    @Test
    void shouldLeaveBlankValuesUntouched() {
        assertNull(SensitiveHeaderMasker.mask(null));
        assertEquals("", SensitiveHeaderMasker.mask(""));
        assertEquals("  ", SensitiveHeaderMasker.mask("  "));
    }
}
