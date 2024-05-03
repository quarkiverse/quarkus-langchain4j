package io.quarkiverse.langchain4j.openshiftai.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.net.URL;
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
@ConfigMapping(prefix = "quarkus.langchain4j.openshift-ai")
public interface LangChain4jOpenshiftAiConfig {

    /**
     * Default model config.
     */
    @WithParentName
    OpenshiftAiConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, OpenshiftAiConfig> namedConfig();

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
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether the OpenShift AI client should log responses
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-responses}")
        Optional<Boolean> logResponses();

        /**
         * Whether to enable the integration. Defaults to {@code true}, which means requests are made to the OpenAI
         * provider.
         * Set to {@code false} to disable all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();

        /**
         * Chat model related settings
         */
        ChatModelConfig chatModel();
    }
}
