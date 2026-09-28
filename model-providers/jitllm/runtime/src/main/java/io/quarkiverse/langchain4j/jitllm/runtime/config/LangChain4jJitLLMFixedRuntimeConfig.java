package io.quarkiverse.langchain4j.jitllm.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_AND_RUN_TIME_FIXED;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.*;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

/**
 * Fixed runtime configuration for JitLLM extension.
 * <p>
 * This configuration is read at build time and remains fixed for the lifetime of the application.
 * It includes settings that cannot be changed after the application is built, such as
 * the model file path. These values are baked into the application during the build process.
 * <p>
 * To change these settings, the application must be rebuilt with the new configuration values.
 * This ensures optimal performance and allows for build-time validation and optimization.
 * <p>
 * Example configuration:
 *
 * <pre>
 * quarkus.langchain4j.jitllm.chat-model.model-path=/path/to/model.gguf
 * </pre>
 * <p>
 * <strong>Note:</strong> These properties must be set in {@code application.properties} at build time
 * and cannot be overridden at runtime through environment variables or system properties.
 */
@ConfigRoot(phase = BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.langchain4j.jitllm")
public interface LangChain4jJitLLMFixedRuntimeConfig {

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

    /**
     * Location on the file-system which serves as a cache for the models
     *
     */
    @ConfigDocDefault("${user.home}/.langchain4j/models")
    Optional<Path> modelsPath();

    @ConfigGroup
    interface JitLLMConfig {

        /**
         * Chat model related settings
         */
        ChatModelFixedRuntimeConfig chatModel();
    }
}