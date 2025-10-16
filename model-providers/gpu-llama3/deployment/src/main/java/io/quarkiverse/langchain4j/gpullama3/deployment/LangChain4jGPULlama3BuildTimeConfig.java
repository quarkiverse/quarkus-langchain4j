package io.quarkiverse.langchain4j.gpullama3.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.gpu-llama3")
public interface LangChain4jGPULlama3BuildTimeConfig {

    /**
     * Chat model related settings
     */
    ChatModelBuildConfig chatModel();
}
