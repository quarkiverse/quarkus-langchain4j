package io.quarkiverse.langchain4j.watsonx.services;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;
import static io.quarkiverse.langchain4j.watsonx.bean.UtilityAgentToolsRequest.ToolName.GOOGLE_SEARCH;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.Experimental;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.watsonx.bean.GoogleSearchResult;
import io.quarkiverse.langchain4j.watsonx.bean.UtilityAgentToolsRequest;
import io.quarkiverse.langchain4j.watsonx.bean.UtilityAgentToolsRequest.StringInput;
import io.quarkiverse.langchain4j.watsonx.bean.UtilityAgentToolsResponse;
import io.quarkiverse.langchain4j.watsonx.client.UtilityAgentToolsRestApi;
import io.quarkiverse.langchain4j.watsonx.exception.BuiltinServiceException;
import io.quarkiverse.langchain4j.watsonx.runtime.config.BuiltinServiceConfig.GoogleSearchConfig;

/**
 * Built-in service to search for online trends, news, current events, real-time information or research topics.
 */
@Experimental
public class GoogleSearchService {

    private static final Logger logger = Logger.getLogger(GoogleSearchService.class);
    private static final ObjectMapper objectMapper = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER;
    private UtilityAgentToolsRestApi client;
    private int maxResults;

    public GoogleSearchService(UtilityAgentToolsRestApi utilityAgentToolRestApi, GoogleSearchConfig config) {
        this.client = utilityAgentToolRestApi;
        this.maxResults = config.maxResults();
    }

    /**
     * Search for online trends, news, current events, real-time information, or research topics.
     *
     * @param url The URL of the web page to fetch.
     * @return {@link List} of {@link GoogleSearchResult} that contain the retrieved web page content.
     */
    public List<GoogleSearchResult> search(String input) throws BuiltinServiceException {
        return search(input, this.maxResults);
    }

    /**
     * Search for online trends, news, current events, real-time information, or research topics.
     *
     * @param url The URL of the web page to fetch.
     * @param maxResults Max number of results.
     * @return {@link List} of {@link GoogleSearchResult} that contain the retrieved web page content.
     */
    public List<GoogleSearchResult> search(String input, Integer maxResults) throws BuiltinServiceException {

        if (Objects.isNull(input) || input.isBlank())
            throw new IllegalArgumentException("The field \"input\" cannot be null or empty");

        if (maxResults == null || maxResults < 0) {
            maxResults = this.maxResults;
        }

        if (maxResults > 20) {
            logger.info("The tool cannot return more than 20 elements, set maxResults to 20");
            maxResults = 20;
        }

        var request = new UtilityAgentToolsRequest(GOOGLE_SEARCH, new StringInput(input), Map.of("maxResults", maxResults));
        var response = retryOn(new Callable<UtilityAgentToolsResponse>() {
            @Override
            public UtilityAgentToolsResponse call() throws Exception {
                return client.run(request);
            }
        });

        try {
            return objectMapper.readValue(response.output(), new TypeReference<List<GoogleSearchResult>>() {
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
