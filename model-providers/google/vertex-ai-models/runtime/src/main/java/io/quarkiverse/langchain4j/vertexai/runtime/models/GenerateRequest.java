package io.quarkiverse.langchain4j.vertexai.runtime.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTool;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerateRequest(String anthropic_version,
        Integer max_tokens,
        List<Message> messages,
        List<AnthropicTool> tools,
        AnthropicThinking thinking) {
    public record Message(
            String role,
            String content) {
    }
}
