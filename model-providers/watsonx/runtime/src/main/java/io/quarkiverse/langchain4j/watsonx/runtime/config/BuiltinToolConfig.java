package io.quarkiverse.langchain4j.watsonx.runtime.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface BuiltinToolConfig {

    /**
     * Base URL for the built-in service.
     * <p>
     * All available URLs are listed in the IBM Watsonx.ai documentation at the
     * <a href="https://cloud.ibm.com/apidocs/watsonx-ai#endpoint-url">following link</a>.
     * <p>
     * <b>Note:</b> If empty, the URL is automatically calculated based on the {@code watsonx.base-url} value.
     */
    Optional<String> baseUrl();

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
     * Whether the watsonx.ai client should log requests as cURL commands.
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.log-requests-curl}")
    Optional<Boolean> logRequestsCurl();

    /**
     * Tavily search configuration.
     */
    TavilySearchConfig tavilySearch();

    /**
     * PythonInterpreter configuration.
     */
    PythonInterpreterConfig pythonInterpreter();

    /**
     * Rag query tool configuration.
     */
    RagQueryConfig ragQuery();

    @ConfigGroup
    public interface TavilySearchConfig {

        /**
         * Tavily API key.
         */
        Optional<String> apiKey();
    }

    @ConfigGroup
    public interface PythonInterpreterConfig {

        /**
         * Deployment id.
         */
        Optional<String> deploymentId();
    }

    @ConfigGroup
    public interface RagQueryConfig {

        /**
         * Vector index ids
         */
        Optional<List<String>> vectorIndexIds();
    }
}
