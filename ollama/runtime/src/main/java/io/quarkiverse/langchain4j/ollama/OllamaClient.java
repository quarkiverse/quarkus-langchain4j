package io.quarkiverse.langchain4j.ollama;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class OllamaClient {

    private final OllamaRestApi restApi;

    public OllamaClient(String baseUrl, Duration timeout, boolean logRequests, boolean logResponses) {
        try {
            // TODO: cache?
            QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(new URI(baseUrl))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);
            if (logRequests || logResponses) {
                builder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                builder.clientLogger(new OllamaRestApi.OllamaLogger(logRequests, logResponses));
            }

            restApi = builder.build(OllamaRestApi.class);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public CompletionResponse completion(CompletionRequest request) {
        return restApi.generate(request);
    }
}
