package io.quarkiverse.langchain4j.openshiftai.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.net.URL;
import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.openshift-ai")
public interface Langchain4jOpenshiftAiConfig {

    /**
     * Base URL where OpenShift AI serving is running, such as
     * {@code https://flant5s-l-predictor-ch2023.apps.cluster-hj2qv.dynamic.redhatworkshops.io:443/api}
     */
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
