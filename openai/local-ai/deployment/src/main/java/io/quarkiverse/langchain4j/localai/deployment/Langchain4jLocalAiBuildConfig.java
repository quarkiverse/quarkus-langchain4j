package io.quarkiverse.langchain4j.localai.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.azure-openai")
public interface Langchain4jLocalAiBuildConfig {

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
}
