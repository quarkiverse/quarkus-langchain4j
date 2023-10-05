package io.quarkiverse.langchain4j.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j")
public interface LangChain4jRuntimeConfig {

    /**
     * Chat model related settings
     */
    ChatModelRuntime chatModel();

    /**
     * Chat model related settings
     */
    LanguageModelRuntime languageModel();
}
