package io.quarkiverse.langchain4j.gpullama3.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_AND_RUN_TIME_FIXED;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

/**
 * Fixed runtime configuration for GPULlama3 extension.
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
 * quarkus.langchain4j.gpu-llama3.chat-model.model-path=/path/to/model.gguf
 * </pre>
 * <p>
 * <strong>Note:</strong> These properties must be set in {@code application.properties} at build time
 * and cannot be overridden at runtime through environment variables or system properties.
 */
@ConfigRoot(phase = BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.langchain4j.gpu-llama3")
public interface LangChain4jGPULlama3FixedRuntimeConfig {

    /**
     * Default model config.
     */
    @WithParentName
    FixedRuntimeConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, FixedRuntimeConfig> namedConfig();

    interface FixedRuntimeConfig {

        /**
         * Chat model related settings
         */
        ChatModelFixedRuntimeConfig chatModel();
    }
}