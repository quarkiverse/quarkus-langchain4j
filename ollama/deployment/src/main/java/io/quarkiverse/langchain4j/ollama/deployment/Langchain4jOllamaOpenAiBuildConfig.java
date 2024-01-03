package io.quarkiverse.langchain4j.ollama.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.ollama")
public interface Langchain4jOllamaOpenAiBuildConfig {

    /**
     * Chat model related settings
     */
    ChatModelBuildConfig chatModel();

    /**
     * Embedding model related settings
     */
    EmbeddingModelBuildConfig embeddingModel();
}
