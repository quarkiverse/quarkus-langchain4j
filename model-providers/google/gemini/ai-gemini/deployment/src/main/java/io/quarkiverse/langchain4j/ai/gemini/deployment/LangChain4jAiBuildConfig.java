package io.quarkiverse.langchain4j.ai.gemini.deployment;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import io.quarkiverse.langchain4j.gemini.common.ChatModelBuildConfig;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.ai.gemini")
public interface LangChain4jAiBuildConfig {
    /**
     * Chat model related settings
     */
    ChatModelBuildConfig chatModel();
}
