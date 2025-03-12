package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface BuiltinServiceConfig {

    /**
     * Base URL for the built-in service.
     * <p>
     * All available URLs are listed in the IBM Watsonx.ai documentation at the
     * <a href="https://cloud.ibm.com/apidocs/watsonx-ai#endpoint-url">following
     * link</a>.
     * <p>
     * <b>Note:</b> If empty, the URL is automatically calculated based on the {@code watsonx.base-url} value.
     */
    Optional<String> baseUrl();

    /**
     * IBM Cloud API key.
     * <p>
     * If empty, the api key inherits the value from the {@code watsonx.api-key} property.
     */
    Optional<String> apiKey();

    /**
     * Timeout for built-in tools APIs.
     * <p>
     * If empty, the api key inherits the value from the {@code watsonx.timeout} property.
     */
    @ConfigDocDefault("10s")
    Optional<Duration> timeout();

    /**
     * Whether the built-in rest client should log requests.
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.log-requests}")
    Optional<Boolean> logRequests();

    /**
     * Whether the built-in rest client should log responses.
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.log-requests}")
    Optional<Boolean> logResponses();

    /**
     * Google search service configuration.
     */
    GoogleSearchConfig googleSearch();

    @ConfigGroup
    public interface GoogleSearchConfig {

        /**
         * Maximum number of search results.
         * <p>
         * <strong>Possible values:</strong> <code>1 < value < 20</code>
         */
        @WithDefault("10")
        int maxResults();
    }
}
