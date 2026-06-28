package io.quarkiverse.langchain4j.vertexai.runtime.models;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerateResponse(String model,
        String id,
        String type,
        String role,
        List<Content> content,
        String stop_reason,
        String stop_sequence,
        Usage usage,
        String output_tokens) {

    public record Content(String type,
            String text, // populated if type is "text"
            String id, // populated if type is "tool_use"
            String name, // populated if type is "tool_use"
            Map<String, Object> input // populated if type is "tool_use" and contains the mist of the input parameters
    ) {
    }

    public record Usage(String input_tokens,
            String cache_creation_input_tokens,
            String cache_read_input_tokens,
            CacheCreation cache_creation) {
    }

    public record CacheCreation(String ephemeral_5m_input_tokens, String ephemeral_1h_input_tokens) {
    }
}
