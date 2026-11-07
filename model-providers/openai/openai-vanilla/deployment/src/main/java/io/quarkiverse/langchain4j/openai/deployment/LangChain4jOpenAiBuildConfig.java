package io.quarkiverse.langchain4j.openai.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.openai")
public interface LangChain4jOpenAiBuildConfig {

    /**
     * Default model config.
     */
    @WithParentName
    OpenAiBuildConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, OpenAiBuildConfig> namedConfig();

    @ConfigGroup
    interface OpenAiBuildConfig {

        /**
         * Chat model related settings
         */
        ChatModelBuildConfig chatModel();

        /**
         * Embedding model related settings
         */
        EmbeddingModelBuildConfig embeddingModel();

        /**
         * Moderation model related settings
         */
        ModerationModelBuildConfig moderationModel();

        /**
         * Image model related settings
         */
        ImageModelBuildConfig imageModel();

        /**
         * Audio transcription model related settings
         */
        AudioTranscriptionModelBuildConfig audioTranscriptionModel();
    }
}
