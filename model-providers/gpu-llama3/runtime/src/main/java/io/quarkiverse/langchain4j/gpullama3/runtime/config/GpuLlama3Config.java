package io.quarkiverse.langchain4j.gpullama3.runtime.config;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.langchain4j.gpu-llama3")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface GpuLlama3Config {

    /**
     * Default (unnamed) configuration.
     */
    GpuLlama3ConfigEntry defaultConfig();

    /**
     * Named configurations.
     */
    Map<String, GpuLlama3ConfigEntry> namedConfig();

    @ConfigGroup
    interface GpuLlama3ConfigEntry {
        /**
         * Enable GPU Llama3 integration.
         */
        boolean enableIntegration();

        /**
         * Path to the model file (GGUF).
         */
        String modelPath();
    }
}
