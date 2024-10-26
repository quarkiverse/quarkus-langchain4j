package io.quarkiverse.langchain4j.llama3.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_AND_RUN_TIME_FIXED;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = BUILD_AND_RUN_TIME_FIXED)
@ConfigMapping(prefix = "quarkus.langchain4j.llama3")
public interface LangChain4jLlama3FixedRuntimeConfig {

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

    /**
     * Location on the file-system which serves as a cache for the models
     *
     */
    @ConfigDocDefault("${user.name}/.llama3java/models")
    Optional<Path> modelsPath();

    @ConfigGroup
    interface Llama3Config {

        /**
         * Chat model related settings
         */
        ChatModelFixedRuntimeConfig chatModel();
    }
}
