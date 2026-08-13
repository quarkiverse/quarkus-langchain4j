package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ReasoningEffort;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ServiceTier;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface GatewayChatModelConfig extends CommonChatModelConfig {

    /**
     * The identifier of the model to use, as configured in the Model Gateway (for example
     * <code>openai/gpt-4o-mini</code>).
     * <p>
     * Setting this property routes all chat requests to the Model Gateway.
     */
    Optional<String> modelName();

    /**
     * Specifies the latency tier used to serve the request.
     * <p>
     * <strong>Allowable values:</strong> <code>[auto, default, flex, priority]</code>
     */
    Optional<ServiceTier> serviceTier();

    /**
     * Constrains the effort spent on reasoning for reasoning models.
     * <p>
     * Reducing the reasoning effort can result in faster responses and fewer tokens used on reasoning.
     * <p>
     * <strong>Allowable values:</strong> <code>[low, medium, high]</code>
     */
    Optional<ReasoningEffort> reasoningEffort();

    /**
     * Configuration of the semantic cache used by the Model Gateway router.
     * <p>
     * Caching is only honored on non-streaming requests.
     */
    Optional<CacheConfig> cache();

    /**
     * The output types that the model is requested to generate.
     * <p>
     * Most models are only able to generate <code>text</code>, which is the default.
     */
    Optional<List<String>> modalities();

    /**
     * Whether the generated output should be stored for model distillation or evaluations.
     */
    Optional<Boolean> store();

    /**
     * Whether the model is allowed to run tool calls in parallel.
     */
    Optional<Boolean> parallelToolCalls();

    /**
     * A stable identifier of the end user issuing the request, used by the backing provider to detect and prevent abuse.
     */
    Optional<String> user();

    /**
     * A set of key/value pairs that is attached to the request and returned with the response.
     */
    @ConfigDocMapKey("metadata-key")
    Map<String, String> metadata();

    @ConfigGroup
    public interface CacheConfig {

        /**
         * Whether the semantic cache is enabled.
         */
        @ConfigDocDefault("true")
        Optional<Boolean> enabled();

        /**
         * The similarity threshold a cached entry must reach to be served instead of calling the model.
         */
        @ConfigDocDefault("provider specific")
        Optional<Double> threshold();
    }
}
