package io.quarkiverse.langchain4j.gemini.common;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gemini {@code thinkingLevel} values, serialized as the lowercase API strings
 * {@code minimal}, {@code low}, {@code medium}, and {@code high}.
 *
 * @see <a href="https://ai.google.dev/gemini-api/docs/thinking">Gemini thinking</a>
 */
public enum ThinkingLevel {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH;

    @JsonValue
    public String toApiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
