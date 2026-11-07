package io.quarkiverse.langchain4j.ollama.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_AND_RUN_TIME_FIXED;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.langchain4j.ollama")
public interface LangChain4jOllamaFixedRuntimeConfig {

    /**
     * Default model config.
     */
    @WithParentName
    OllamaConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, OllamaConfig> namedConfig();

    interface OllamaConfig {

        /**
         * Chat model related settings
         */
        ChatModelFixedRuntimeConfig chatModel();

        /**
         * Embedding model related settings
         */
        EmbeddingModelFixedRuntimeConfig embeddingModel();
    }
}
