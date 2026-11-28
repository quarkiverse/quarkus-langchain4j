package io.quarkiverse.langchain4j.cost;

import dev.langchain4j.model.output.TokenUsage;
import io.smallrye.common.annotation.Experimental;

/**
 * SPI that exposes a provider's reasoning (thinking) token count in a provider-neutral way. Implement it in the provider
 * module (where the provider {@link TokenUsage} subtype is available) and expose it as a CDI bean; the core consults
 * all available extractors, so it never depends on a specific provider.
 * <p>
 * A provider can implement this interface and {@link CacheTokenUsageExtractor} on the same bean.
 */
@Experimental("This feature is experimental and the API is subject to change")
public interface ReasoningTokenUsageExtractor {

    /**
     * Whether this extractor is able to read the reasoning token count from the given usage.
     */
    boolean supports(TokenUsage tokenUsage);

    /**
     * Returns the number of output tokens the model spent on reasoning, or {@code null} if unknown. Only called when
     * {@link #supports(TokenUsage)} returns {@code true}.
     */
    Integer reasoningOutputTokens(TokenUsage tokenUsage);

    /**
     * Whether {@link TokenUsage#outputTokenCount()} already includes the reasoning tokens, as the OpenTelemetry GenAI
     * semantic conventions require for {@code gen_ai.usage.output_tokens}. Providers that report reasoning tokens
     * separately from the output tokens return {@code false}, so that telemetry adds them to the output count.
     */
    default boolean outputTokenCountIncludesReasoning() {
        return true;
    }
}
