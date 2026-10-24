package io.quarkiverse.langchain4j.mockapi.internal.dialect.openai;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.Usage;

/**
 * JSON helpers shared by the OpenAI Responses and Decisions renderers.
 */
final class OpenAiJson {

    private OpenAiJson() {
    }

    /**
     * Responses and Decisions {@code ResponseUsage}.
     */
    static void responsesUsage(ObjectNode root, Usage usage) {
        int in = usage == null ? 0 : usage.inputTokens();
        int out = usage == null ? 0 : usage.outputTokens();
        ObjectNode u = root.putObject("usage");
        u.put("input_tokens", in);
        u.putObject("input_tokens_details").put("cached_tokens", 0).put("cache_write_tokens", 0);
        u.put("output_tokens", out);
        u.putObject("output_tokens_details").put("reasoning_tokens", 0);
        u.put("total_tokens", in + out);
    }
}
