package io.quarkiverse.langchain4j.jlama.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.jlama")
public interface LangChain4jJlamaConfig {

    /**
     * Default model config.
     */
    @WithParentName
    JlamaConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, JlamaConfig> namedConfig();

    @ConfigGroup
    interface JlamaConfig {

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
    }
}
