package io.quarkiverse.langchain4j.ai.runtime.gemini.config;

/**
 * Gemini {@code thinkingLevel} values. Serialized as {@code minimal}, {@code low}, {@code medium}, or {@code high}.
 *
 * @see <a href="https://ai.google.dev/gemini-api/docs/thinking">Gemini thinking</a>
 */
public enum ThinkingLevel {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH
}
