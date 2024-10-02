package io.quarkiverse.langchain4j.tavily;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.ClientLogger;
import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import io.quarkiverse.langchain4j.tavily.runtime.TavilyClient;
import io.quarkiverse.langchain4j.tavily.runtime.TavilyResponse;
import io.quarkiverse.langchain4j.tavily.runtime.TavilySearchRequest;
import io.quarkiverse.langchain4j.tavily.runtime.TavilySearchResult;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

// TODO: use the upstream implementation once it doesn't depend on OkHttp etc.
public class QuarkusTavilyWebSearchEngine implements WebSearchEngine {

    private final TavilyClient tavilyClient;
    private final String apiKey;
    private final Integer maxResults;
    private final SearchDepth searchDepth;
    private final boolean includeAnswer;
    private final boolean includeRawContent;
    private final List<String> includeDomains;
    private final List<String> excludeDomains;

    public QuarkusTavilyWebSearchEngine(String baseUrl,
            String apiKey,
            Integer maxResults,
            Duration timeout,
            boolean logRequests,
            boolean logResponses,
            SearchDepth searchDepth,
            boolean includeAnswer,
            boolean includeRawContent,
            List<String> includeDomains,
            List<String> excludeDomains) {
        this.apiKey = apiKey;
        this.maxResults = maxResults;
        this.searchDepth = searchDepth;
        this.includeAnswer = includeAnswer;
        this.includeRawContent = includeRawContent;
        this.includeDomains = includeDomains;
        this.excludeDomains = excludeDomains;
        try {
            QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(new URI(baseUrl))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);
            if (logRequests || logResponses) {
                builder = builder
                        .loggingScope(LoggingScope.REQUEST_RESPONSE)
                        .clientLogger(new TavilyClientLogger(logRequests, logResponses));
            }
            tavilyClient = builder.build(TavilyClient.class);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        TavilySearchRequest tavilySearchRequest = new TavilySearchRequest(
                apiKey,
                webSearchRequest.searchTerms(),
                searchDepth.toString().toLowerCase(),
                includeAnswer,
                includeRawContent,
                webSearchRequest.maxResults() != null ? webSearchRequest.maxResults() : maxResults,
                includeDomains,
                excludeDomains);
        TavilyResponse tavilyResponse = tavilyClient.search(tavilySearchRequest);

        final List<WebSearchOrganicResult> results = tavilyResponse.getResults().stream()
                .map(QuarkusTavilyWebSearchEngine::toWebSearchOrganicResult)
                .collect(toList());

        if (tavilyResponse.getAnswer() != null) {
            WebSearchOrganicResult answerResult = WebSearchOrganicResult.from(
                    "Tavily Search API",
                    URI.create("https://tavily.com/"),
                    tavilyResponse.getAnswer(),
                    null);
            results.add(0, answerResult);
        }

        return WebSearchResults.from(WebSearchInformationResult.from((long) results.size()), results);
    }

    private static WebSearchOrganicResult toWebSearchOrganicResult(TavilySearchResult tavilySearchResult) {
        return WebSearchOrganicResult.from(tavilySearchResult.getTitle(),
                URI.create(tavilySearchResult.getUrl().replaceAll(" ", "%20")),
                tavilySearchResult.getContent(),
                tavilySearchResult.getRawContent(),
                Collections.singletonMap("score", String.valueOf(tavilySearchResult.getScore())));
    }

    static class TavilyClientLogger implements ClientLogger {
        private static final Logger log = Logger.getLogger(TavilyClientLogger.class);

        private final boolean logRequests;
        private final boolean logResponses;

        public TavilyClientLogger(boolean logRequests, boolean logResponses) {
            this.logRequests = logRequests;
            this.logResponses = logResponses;
        }

        @Override
        public void setBodySize(int bodySize) {
            // ignore
        }

        @Override
        public void logRequest(HttpClientRequest request, Buffer body, boolean omitBody) {
            if (!logRequests || !log.isInfoEnabled()) {
                return;
            }
            try {
                log.infof("Request:\n- method: %s\n- url: %s\n- headers: %s\n- body: %s",
                        request.getMethod(),
                        request.absoluteURI(),
                        inOneLine(request.headers()),
                        bodyToString(body));
            } catch (Exception e) {
                log.warn("Failed to log request", e);
            }
        }

        @Override
        public void logResponse(HttpClientResponse response, boolean redirect) {
            if (!logResponses || !log.isInfoEnabled()) {
                return;
            }
            response.bodyHandler(new io.vertx.core.Handler<>() {
                @Override
                public void handle(Buffer body) {
                    try {
                        log.infof(
                                "Response:\n- status code: %s\n- headers: %s\n- body: %s",
                                response.statusCode(),
                                inOneLine(response.headers()),
                                bodyToString(body));
                    } catch (Exception e) {
                        log.warn("Failed to log response", e);
                    }
                }
            });
        }

        private String bodyToString(Buffer body) {
            if (body == null) {
                return "";
            }

            return ShadowSensitiveData.process(body, "api_key");
        }

        private String inOneLine(io.vertx.core.MultiMap headers) {

            return stream(headers.spliterator(), false)
                    .map(header -> {
                        String headerKey = header.getKey();
                        String headerValue = header.getValue();
                        return String.format("[%s: %s]", headerKey, headerValue);
                    })
                    .collect(joining(", "));
        }

    }
}
