package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ScoringModelConfig {

    /**
     * The id of the model to be used.
     * <p>
     * All available models are listed in the IBM Watsonx.ai documentation at the <a href="
     * https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models-embed.html?context=wx&audience=wdp#reranker-overview">following
     * link</a>.
     * <p>
     * To use a model, locate the <code>API model_id</code> column in the table and copy the corresponding model ID.
     */
    @WithDefault("cross-encoder/ms-marco-minilm-l-12-v2")
    String modelId();

    /**
     * Specifies the maximum number of input tokens accepted. This helps to avoid requests failing due to input exceeding the
     * configured token limits.
     * <p>
     * If the input exceeds the specified token limit, the text will be truncated from the end (right side), ensuring that the
     * start of the input remains
     * intact. If the provided value exceeds the model's maximum sequence length (refer to the documentation for the model's
     * maximum sequence length), the
     * request will fail if the total number of tokens exceeds the maximum limit.
     */
    Optional<Integer> truncateInputTokens();

    /**
     * Whether embedding model requests should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether embedding model responses should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();
}
