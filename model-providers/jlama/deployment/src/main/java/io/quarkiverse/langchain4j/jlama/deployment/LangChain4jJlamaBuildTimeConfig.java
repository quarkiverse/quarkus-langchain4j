package io.quarkiverse.langchain4j.jlama.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.jlama")
public interface LangChain4jJlamaBuildTimeConfig {

    /**
     * Determines whether the necessary Jlama models are downloaded and included in the jar at build time.
     * Currently, this option is only valid for {@code fast-jar} deployments.
     */
    @WithDefault("true")
    boolean includeModelsInArtifact();

    /**
     * Chat model related settings
     */
    ChatModelBuildConfig chatModel();

    /**
     * Embedding model related settings
     */
    EmbeddingModelBuildConfig embeddingModel();

}
