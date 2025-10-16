package io.quarkiverse.langchain4j.gpullama3.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_AND_RUN_TIME_FIXED;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.langchain4j.gpu-llama3")
public interface LangChain4jGPULlama3FixedRuntimeConfig {

    /**
     * Default model config.
     */
    @WithParentName
    GpuLlama3Config defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, GpuLlama3Config> namedConfig();

    interface GpuLlama3Config {

        /**
         * Chat model related settings
         */
        ChatModelFixedRuntimeConfig chatModel();
    }
}