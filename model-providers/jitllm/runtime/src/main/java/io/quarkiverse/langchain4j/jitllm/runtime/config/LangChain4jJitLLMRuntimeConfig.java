package io.quarkiverse.langchain4j.jitllm.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

/**
 * Runtime configuration for JitLLM extension.
 * <p>
 * This configuration is read at runtime and can be changed without rebuilding the application.
 * It includes dynamic settings such as model parameters (temperature, max tokens),
 * logging preferences, and integration control.
 */
@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.jitllm")
public interface LangChain4jJitLLMRuntimeConfig {

    /**
     * Default model config.
     */
    @WithParentName
    JitLLMConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, JitLLMConfig> namedConfig();

    interface JitLLMConfig {

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
         * Whether JitLLM should log requests
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether JitLLM client should log responses
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-responses}")
        Optional<Boolean> logResponses();
    }
}