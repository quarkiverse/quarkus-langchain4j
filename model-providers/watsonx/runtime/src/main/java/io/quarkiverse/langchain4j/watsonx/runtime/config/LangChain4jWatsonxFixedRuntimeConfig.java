package io.quarkiverse.langchain4j.watsonx.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_AND_RUN_TIME_FIXED;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.langchain4j.watsonx")
public interface LangChain4jWatsonxFixedRuntimeConfig {

    /**
     * Default model config.
     */
    @WithParentName
    WatsonConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, WatsonConfig> namedConfig();

    interface WatsonConfig {

        /**
         * Specifies the mode of interaction with the LLM model.
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
}
