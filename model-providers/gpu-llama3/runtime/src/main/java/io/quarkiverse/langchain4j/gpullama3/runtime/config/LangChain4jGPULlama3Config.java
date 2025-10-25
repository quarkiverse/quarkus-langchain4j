package io.quarkiverse.langchain4j.gpullama3.runtime.config;

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
 * Runtime configuration for GPULlama3 extension.
 * <p>
 * This configuration is read at runtime and can be changed without rebuilding the application.
 * It includes dynamic settings such as model parameters (temperature, max tokens),
 * logging preferences, and integration control.
 * <p>
 * Properties can be overridden at runtime through environment variables, system properties,
 * or external configuration files.
 * <p>
 * Example configuration:
 *
 * <pre>
 * quarkus.langchain4j.gpu-llama3.enable-integration=true
 * quarkus.langchain4j.gpu-llama3.chat-model.temperature=0.7
 * quarkus.langchain4j.gpu-llama3.chat-model.max-tokens=512
 * quarkus.langchain4j.gpu-llama3.log-requests=true
 * </pre>
 */
@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.gpu-llama3")
public interface LangChain4jGPULlama3Config {

    /**
     * Default model config.
     */
    @WithParentName
    RuntimeConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, RuntimeConfig> namedConfig();

    interface RuntimeConfig {

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
         * Whether GPU Llama3 should log requests
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether GPU Llama3 client should log responses
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-responses}")
        Optional<Boolean> logResponses();
    }
}