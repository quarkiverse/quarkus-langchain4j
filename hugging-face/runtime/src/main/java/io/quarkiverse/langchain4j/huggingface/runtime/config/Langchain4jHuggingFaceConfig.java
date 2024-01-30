package io.quarkiverse.langchain4j.huggingface.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
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
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j")
public interface Langchain4jHuggingFaceConfig {

    /**
     * Default model config.
     */
    @WithName("huggingface")
    HuggingFaceConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, HuggingFaceOuterNamedConfig> namedConfig();

    @ConfigGroup
    interface HuggingFaceConfig {
        /**
         * HuggingFace API key
         */
        @WithDefault("dummy") // TODO: this should be optional but Smallrye Config doesn't like it
        String apiKey();

        /**
         * Timeout for HuggingFace calls
         */
        @WithDefault("10s")
        Duration timeout();

        /**
         * Chat model related settings
         */
        ChatModelConfig chatModel();

        /**
         * Embedding model related settings
         */
        EmbeddingModelConfig embeddingModel();

        /**
         * Whether the HuggingFace client should log requests
         */
        @ConfigDocDefault("false")
        Optional<Boolean> logRequests();

        /**
         * Whether the HuggingFace client should log responses
         */
        @ConfigDocDefault("false")
        Optional<Boolean> logResponses();
    }

    interface HuggingFaceOuterNamedConfig {
        /**
         * Config for the specified name
         */
        @WithName("huggingface")
        HuggingFaceConfig huggingFace();
    }
}
