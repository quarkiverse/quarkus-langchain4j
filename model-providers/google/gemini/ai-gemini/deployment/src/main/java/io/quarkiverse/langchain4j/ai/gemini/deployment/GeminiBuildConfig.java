package io.quarkiverse.langchain4j.ai.gemini.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.ai.gemini")
public interface GeminiBuildConfig {

    /**
     * Chat model related settings
     */
    ChatModelBuildConfig chatModel();

    /**
     * Embedding model related settings
     */
    LangChain4jAiEmbeddingConfig embeddingModel();
}
