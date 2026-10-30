package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelConfig extends FoundationChatModelConfig {

    /**
     * Specifies the model to use for the chat completion.
     * <p>
     * A list of all available models is provided in the IBM watsonx.ai documentation at the
     * <a href="https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx#ibm-provided">this
     * link</a>.
     * <p>
     * To use a model, locate the <code>API model ID</code> column in the table and copy the corresponding model ID.
     */
    @WithDefault("ibm/granite-4-h-small")
    String modelName();

    /**
     * Moderation applied by watsonx.ai while the chat request is served.
     * <p>
     * Unlike the standalone moderation model, these detectors run inside the chat request itself: the input is checked before
     * the model is called and
     * the output is checked before it is returned.
     * <p>
     * This is only supported by the models served directly by watsonx.ai, so it is not available when using the Model Gateway
     * or an on-demand
     * deployment.
     */
    Optional<ModerationsConfig> moderations();
}
