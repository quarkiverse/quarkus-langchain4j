package io.quarkiverse.langchain4j.sample.mockapi;

/**
 * The structured output of {@link SupportAssistant#triage(String)}.
 */
public record Triage(String category, boolean urgent, String summary) {
}
