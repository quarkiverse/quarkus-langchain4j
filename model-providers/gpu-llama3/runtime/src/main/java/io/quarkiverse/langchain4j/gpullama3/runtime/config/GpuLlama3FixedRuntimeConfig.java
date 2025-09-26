package io.quarkiverse.langchain4j.gpullama3.runtime.config;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.langchain4j.gpu-llama3")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface GpuLlama3FixedRuntimeConfig {

    /**
     * Default (unnamed) configuration.
     */
    GpuLlama3FixedConfig defaultConfig();

    /**
     * Named configurations.
     */
    Map<String, GpuLlama3FixedConfig> namedConfig();

    interface GpuLlama3FixedConfig {
        /**
         * Path to the model file (GGUF).
         */
        String modelPath();
    }
}