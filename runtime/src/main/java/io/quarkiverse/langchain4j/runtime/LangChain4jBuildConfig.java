package io.quarkiverse.langchain4j.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_AND_RUN_TIME_FIXED;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.langchain4j")
public interface LangChain4jBuildConfig {

    /**
     * Chat model
     */
    ChatModelBuildTime chatModel();

    /**
     * Language model
     */
    LanguageModelBuild languageModel();

    /**
     * Embedding model
     */
    EmbeddingModelBuild embeddingModel();

    /**
     * Moderation model
     */
    ModerationModelBuild moderationModel();
}
