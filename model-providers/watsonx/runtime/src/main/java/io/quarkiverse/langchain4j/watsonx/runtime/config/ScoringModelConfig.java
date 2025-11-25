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
    String modelName();

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

    /**
     * Whether the watsonx.ai client should log requests as cURL commands.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequestsCurl();
}
