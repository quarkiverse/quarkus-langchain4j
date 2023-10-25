package io.quarkiverse.langchain4j.deployment.config;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j")
public interface LangChain4jBuildConfig {

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
}
