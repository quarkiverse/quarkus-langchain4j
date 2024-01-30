package io.quarkiverse.langchain4j.deployment.config;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j")
public interface LangChain4jBuildConfig {

    /**
     * Default model config.
     */
    @WithParentName
    @ConfigDocSection
    BaseConfig defaultConfig();

    /**
     * Named model config.
     */
    @WithParentName
    @ConfigDocMapKey("model-name")
    @ConfigDocSection
    Map<String, BaseConfig> namedConfig();

    interface BaseConfig {
        /**
         * Chat model
         */
        ChatModelConfig chatModel();

        /**
         * Embedding model
         */
        EmbeddingModelConfig embeddingModel();

        /**
         * Moderation model
         */
        ModerationModelConfig moderationModel();

        /**
         * Image model
         */
        ImageModelConfig imageModel();
    }
}
