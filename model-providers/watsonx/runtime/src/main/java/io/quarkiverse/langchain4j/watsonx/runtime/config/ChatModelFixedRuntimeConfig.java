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
    @WithDefault("ibm/granite-13b-chat-v2")
    String modelId();

    /**
     * Configuration property that enables or disables the functionality of the prompt formatter.
     *
     * <ul>
     * <li><code>true</code>: When enabled, prompts are automatically enriched with the specific tags defined by the model.</li>
     * <li><code>false</code>: Prompts will not be enriched with the model's tags.</li>
     * </ul>
     */
    @WithDefault("true")
    boolean promptFormatter();
}
