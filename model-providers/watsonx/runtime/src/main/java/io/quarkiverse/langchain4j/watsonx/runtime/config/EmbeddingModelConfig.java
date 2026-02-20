package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * Specifies the ID of the model to be used.
     * <p>
     * A list of all available models is provided in the IBM watsonx.ai documentation at the
     * <a href=
     * "https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models-embed.html?context=wx&audience=wdp#ibm-provided">this
     * link</a>.
     * <p>
     * To use a model, locate the <code>API model ID</code> column in the table and copy the corresponding model ID.
     */
    @WithDefault("ibm/granite-embedding-278m-multilingual")
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
