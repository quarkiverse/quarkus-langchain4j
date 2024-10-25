package io.quarkiverse.langchain4j.watsonx.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelFixedRuntimeConfig {

    /**
     * Model id to use.
     *
     * <p>
     * To view the complete model list, <a href=
     * "https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-api-model-ids.html?context=wx&audience=wdp#model-ids">click
     * here</a>.
     */
    @WithDefault("mistralai/mistral-large")
    String modelId();

    /**
     * Specifies the mode of interaction with the selected model.
     * <p>
     * This property allows you to choose between two modes of operation:
     * <ul>
     * <li><strong>chat</strong>: prompts are automatically enriched with the specific tags defined by the model</li>
     * <li><strong>generation</strong>: prompts require manual specification of tags</li>
     * </ul>
     * <strong>Allowable values:</strong> <code>[chat, generation]</code>
     */
    @WithDefault("chat")
    String mode();
}
