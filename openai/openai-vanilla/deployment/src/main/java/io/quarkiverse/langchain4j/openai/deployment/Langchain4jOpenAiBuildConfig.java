package io.quarkiverse.langchain4j.openai.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.openai")
public interface Langchain4jOpenAiBuildConfig {

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
}
