package io.quarkiverse.langchain4j.openshiftai.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.net.URL;
import java.time.Duration;
import java.util.Map;

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
public interface Langchain4jOpenshiftAiConfig {

    /**
     * Default model config.
     */
    @WithName("openshift-ai")
    OpenshiftAiConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, OpenshiftAiOuterNamedConfig> namedConfig();

    @ConfigGroup
    interface OpenshiftAiConfig {
        /**
         * Base URL where OpenShift AI serving is running, such as
         * {@code https://flant5s-l-predictor-ch2023.apps.cluster-hj2qv.dynamic.redhatworkshops.io:443/api}
         */
        @WithDefault("https://dummy.ai/api") // TODO: this should be Optional but Smallrye Config doesn't like it
        URL baseUrl();

        /**
         * Timeout for OpenShift AI calls
         */
        @WithDefault("10s")
        Duration timeout();

        /**
         * Whether the OpenShift AI client should log requests
         */
        @WithDefault("false")
        Boolean logRequests();

        /**
         * Whether the OpenShift AI client should log responses
         */
        @WithDefault("false")
        Boolean logResponses();

        /**
         * Chat model related settings
         */
        ChatModelConfig chatModel();
    }

    interface OpenshiftAiOuterNamedConfig {
        /**
         * Config for the specified name
         */
        @WithName("openshift-ai")
        OpenshiftAiConfig openshiftAi();
    }
}
