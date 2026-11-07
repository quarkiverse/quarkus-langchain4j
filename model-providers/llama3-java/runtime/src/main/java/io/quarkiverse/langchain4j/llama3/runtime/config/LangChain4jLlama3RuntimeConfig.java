package io.quarkiverse.langchain4j.llama3.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.llama3")
public interface LangChain4jLlama3RuntimeConfig {

    /**
     * Default model config.
     */
    @WithParentName
    Llama3Config defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, Llama3Config> namedConfig();

    @ConfigGroup
    interface Llama3Config {

        /**
         * Chat model related settings
         */
        ChatModelConfig chatModel();

        /**
         * Whether to enable the integration. Set to {@code false} to disable
         * all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();

        /**
         * Whether Jlama should log requests
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether Jlama client should log responses
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-responses}")
        Optional<Boolean> logResponses();
    }

}
