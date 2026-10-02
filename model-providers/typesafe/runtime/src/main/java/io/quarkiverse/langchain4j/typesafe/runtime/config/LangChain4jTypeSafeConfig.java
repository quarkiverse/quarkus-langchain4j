package io.quarkiverse.langchain4j.typesafe.runtime.config;

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
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.typesafe")
public interface LangChain4jTypeSafeConfig {

    /**
     * Default model config.
     */
    @WithParentName
    TypeSafeConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, TypeSafeConfig> namedConfig();

    @ConfigGroup
    interface TypeSafeConfig {

        /**
         * Base URL of the TypeSafe System One API. Set it to use another server that implements the same API.
         */
        @WithDefault("https://api.typesafe.ai")
        String baseUrl();

        /**
         * TypeSafe API key. Required when using the default base URL, optional for other servers.
         */
        Optional<String> apiKey();

        /**
         * Timeout for TypeSafe calls
         */
        @ConfigDocDefault("10s")
        @WithDefault("${quarkus.langchain4j.timeout}")
        Optional<Duration> timeout();

        /**
         * Whether the TypeSafe client should log requests
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether the TypeSafe client should log responses
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-responses}")
        Optional<Boolean> logResponses();

        /**
         * Decision model related settings
         */
        DecisionModelConfig decisionModel();
    }
}
