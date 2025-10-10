package io.quarkiverse.langchain4j.tavily.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.quarkiverse.langchain4j.tavily.SearchDepth;
import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.tavily")
public interface TavilyConfig {

    /**
     * Base URL of the Tavily API
     */
    @WithDefault("https://api.tavily.com")
    String baseUrl();

    /**
     * API key for the Tavily API
     */
    String apiKey();

    /**
     * Maximum number of results to return
     */
    @WithDefault("5")
    Integer maxResults();

    /**
     * The timeout duration for Tavily requests.
     */
    // Note: probably should not default to the value of the global quarkus.langchain4j.timeout,
    // because 10 seconds is too short for Tavily
    @WithDefault("60s")
    Duration timeout();

    /**
     * Whether requests to Tavily should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.log-requests}")
    Optional<Boolean> logRequests();

    /**
     * Whether responses from Tavily should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.log-responses}")
    Optional<Boolean> logResponses();

    /**
     * The search depth to use. This can be "basic" or "advanced".
     * Basic is the default.
     */
    @WithDefault("BASIC")
    SearchDepth searchDepth();

    /**
     * Include a short answer to original query. Default is false.
     */
    @WithDefault("false")
    boolean includeAnswer();

    /**
     * Include the cleaned and parsed HTML content of each search result. Default is false.
     */
    @WithDefault("false")
    boolean includeRawContent();

    /**
     * A list of domains to specifically include in the search results. Default is [], which includes all domains.
     */
    @WithDefault("[]")
    List<String> includeDomains();

    /**
     * A list of domains to specifically exclude from the search results. Default is [], which doesn't exclude any domains.
     */
    @WithDefault("[]")
    List<String> excludeDomains();

}
