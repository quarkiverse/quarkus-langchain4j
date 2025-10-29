package io.quarkiverse.langchain4j.gpullama3.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.gpu-llama3")
public interface LangChain4jGPULlama3BuildTimeConfig {

    /**
     * Determines whether the necessary GPULlama3 models are downloaded and included in the jar at build time.
     * Currently, this option is only valid for {@code fast-jar} deployments.
     */
    @WithDefault("true")
    boolean includeModelsInArtifact();

    /**
     * Chat model related settings
     */
    ChatModelBuildConfig chatModel();
}
