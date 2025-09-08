package io.quarkiverse.langchain4j.gpullama3.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.gpullama3")
public interface GpuLlama3BuildTimeConfig {

    /**
     * Determines whether the necessary GpuLlama3 models are downloaded and included in the jar at build time.
     * Currently, this option is only valid for {@code fast-jar} deployments.
     */
    @WithDefault("true")
    boolean includeModelsInArtifact();

    /**
     * Chat model related settings
     */
    GpuLlama3ChatModelBuildConfig chatModel();

    /**
     * Embedding model related settings
     */
    GpuLlama3EmbeddingModelBuildConfig embeddingModel();

}
