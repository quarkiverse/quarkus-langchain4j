package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * Model id to use.
     *
     * To view the complete model list, <a href=
     * "https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models-embed.html?context=wx&audience=wdp">click
     * here</a>.
     */
    @WithDefault("ibm/slate-125m-english-rtrvr")
    String modelId();

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
