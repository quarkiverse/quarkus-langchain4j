package io.quarkiverse.langchain4j.watsonx.services;

import static io.quarkiverse.langchain4j.watsonx.WatsonxUtils.retryOn;
import static io.quarkiverse.langchain4j.watsonx.bean.UtilityAgentToolsRequest.ToolName.WEB_CRAWLER;

import java.util.Objects;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.Experimental;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.watsonx.bean.UtilityAgentToolsRequest;
import io.quarkiverse.langchain4j.watsonx.bean.UtilityAgentToolsRequest.WebCrawlerInput;
import io.quarkiverse.langchain4j.watsonx.bean.UtilityAgentToolsResponse;
import io.quarkiverse.langchain4j.watsonx.bean.WebCrawlerResult;
import io.quarkiverse.langchain4j.watsonx.client.UtilityAgentToolsRestApi;
import io.quarkiverse.langchain4j.watsonx.exception.BuiltinServiceException;

/**
 * Built-in service for fetching the content of a single web page.
 */
@Experimental
public class WebCrawlerService {

    private static final ObjectMapper objectMapper = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER;
    private UtilityAgentToolsRestApi client;

    public WebCrawlerService(UtilityAgentToolsRestApi utilityAgentToolRestApi) {
        this.client = utilityAgentToolRestApi;
    }

    /**
     * Fetches the content of a single web page.
     *
     * @param url The URL of the web page to fetch.
     * @return A {@code WebCrawlerToolResult} containing the retrieved web page content.
     */
    public WebCrawlerResult process(String url) throws BuiltinServiceException {

        if (Objects.isNull(url) || url.isBlank())
            throw new IllegalArgumentException("The field \"url\" cannot be null or empty");

        var request = new UtilityAgentToolsRequest(WEB_CRAWLER, new WebCrawlerInput(url));
        var response = retryOn(new Callable<UtilityAgentToolsResponse>() {
            @Override
            public UtilityAgentToolsResponse call() throws Exception {
                return client.run(request);
            }
        });
        try {
            return objectMapper.readValue(
                    // Convert the double-escaped JSON string into a properly formatted JSON string.
                    objectMapper.readValue(response.output(), String.class),
                    // Deserialize into WebCrawlerToolResult record
                    WebCrawlerResult.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
