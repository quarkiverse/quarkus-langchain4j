package io.quarkiverse.langchain4j.tavily;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder;
import io.quarkus.tls.TlsConfiguration;

public class QuarkusTavilyWebSearchEngine implements WebSearchEngine {

    private final TavilyWebSearchEngine delegate;
    private final Integer maxResults;

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
            List<String> excludeDomains,
            Proxy proxy,
            TlsConfiguration tlsConfiguration) {
        this.maxResults = maxResults;
        JaxRsHttpClientBuilder httpClientBuilder = new JaxRsHttpClientBuilder()
                .proxy(proxy)
                .tlsConfiguration(tlsConfiguration);
        this.delegate = TavilyWebSearchEngine.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .timeout(timeout)
                .searchDepth(searchDepth.toString().toLowerCase(Locale.ROOT))
                .includeAnswer(includeAnswer)
                .includeRawContent(includeRawContent)
                .includeDomains(includeDomains)
                .excludeDomains(excludeDomains)
                .httpClientBuilder(new TavilyHttpClientBuilder(httpClientBuilder, logRequests, logResponses))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        return delegate.search(withEffectiveMaxResults(webSearchRequest));
    }

    @Override
    public CompletableFuture<WebSearchResults> searchAsync(WebSearchRequest webSearchRequest) {
        return delegate.searchAsync(withEffectiveMaxResults(webSearchRequest));
    }

    private WebSearchRequest withEffectiveMaxResults(WebSearchRequest webSearchRequest) {
        Integer effectiveMaxResults = webSearchRequest.maxResults() != null ? webSearchRequest.maxResults() : maxResults;
        return WebSearchRequest.builder()
                .searchTerms(webSearchRequest.searchTerms())
                .maxResults(effectiveMaxResults)
                .language(webSearchRequest.language())
                .geoLocation(webSearchRequest.geoLocation())
                .startPage(webSearchRequest.startPage())
                .startIndex(webSearchRequest.startIndex())
                .safeSearch(webSearchRequest.safeSearch())
                .additionalParams(webSearchRequest.additionalParams())
                .build();
    }
}
