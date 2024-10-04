package io.quarkiverse.langchain4j.tavily.runtime;

import java.util.function.Supplier;

import io.quarkiverse.langchain4j.tavily.QuarkusTavilyWebSearchEngine;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class TavilyRecorder {

    public Supplier<QuarkusTavilyWebSearchEngine> tavilyEngineSupplier(TavilyConfig config) {
        return new Supplier<>() {
            @Override
            public QuarkusTavilyWebSearchEngine get() {
                return new QuarkusTavilyWebSearchEngine(config.baseUrl(),
                        config.apiKey(),
                        config.maxResults(),
                        config.timeout(),
                        config.logRequests().orElse(false),
                        config.logResponses().orElse(false),
                        config.searchDepth(),
                        config.includeAnswer(),
                        config.includeRawContent(),
                        config.includeDomains(),
                        config.excludeDomains());
            }
        };
    }
}
