package io.quarkiverse.langchain4j.ollama;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.model.ollama.*;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Multi;

public class QuarkusOllamaClient implements OllamaClient {

    private final OllamaRestApi restApi;

    public QuarkusOllamaClient(String baseUrl, Duration timeout, boolean logRequests, boolean logResponses) {
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

    public ChatResponse chat(ChatRequest request) {
        return restApi.chat(request);
    }

    public Multi<ChatResponse> streamingChat(ChatRequest request) {
        return restApi.streamingChat(request);
    }

    public EmbeddingResponse embed(EmbeddingRequest request) {
        return restApi.embeddings(request);
    }
}
