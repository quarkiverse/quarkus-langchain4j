package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.vertexai.gemini")
public interface LangChain4jVertexAiBuildConfig {
    /**
     * Chat model related settings
     */
    ChatModelBuildConfig chatModel();
}
