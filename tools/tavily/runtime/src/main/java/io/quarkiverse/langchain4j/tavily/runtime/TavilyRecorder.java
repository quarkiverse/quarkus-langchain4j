package io.quarkiverse.langchain4j.tavily.runtime;

import java.util.function.Supplier;

import io.quarkiverse.langchain4j.tavily.QuarkusTavilyWebSearchEngine;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class TavilyRecorder {
    private final RuntimeValue<TavilyConfig> runtimeConfig;

    public TavilyRecorder(RuntimeValue<TavilyConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<QuarkusTavilyWebSearchEngine> tavilyEngineSupplier() {
        return new Supplier<>() {
            @Override
            public QuarkusTavilyWebSearchEngine get() {
                return new QuarkusTavilyWebSearchEngine(runtimeConfig.getValue().baseUrl(),
                        runtimeConfig.getValue().apiKey(),
                        runtimeConfig.getValue().maxResults(),
                        runtimeConfig.getValue().timeout(),
                        runtimeConfig.getValue().logRequests().orElse(false),
                        runtimeConfig.getValue().logResponses().orElse(false),
                        runtimeConfig.getValue().searchDepth(),
                        runtimeConfig.getValue().includeAnswer(),
                        runtimeConfig.getValue().includeRawContent(),
                        runtimeConfig.getValue().includeDomains(),
                        runtimeConfig.getValue().excludeDomains());
            }
        };
    }
}
