package io.quarkiverse.langchain4j.deployment.config;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
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

    /**
     * DevServices related configuration
     */
    DevServicesConfig devservices();

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

    @ConfigGroup
    interface DevServicesConfig {
        /**
         * If DevServices has been explicitly enabled or disabled. DevServices is generally enabled
         * by default, unless there is an existing configuration present.
         * <p>
         * When DevServices is enabled Quarkus will attempt to automatically serve a model if there are any matching ones.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * The default port where the inference server listens for requests
         */
        @WithDefault("11434")
        Integer port();
    }
}
