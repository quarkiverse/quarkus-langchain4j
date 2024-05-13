package io.quarkiverse.langchain4j.deployment.devservice;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Flow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import io.smallrye.mutiny.operators.multi.builders.EmitterBasedMulti;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;

/**
 * An implementation of {@link OllamaClient} based on the JDK client.
 * We can't use our REST Client here because it's not available during augmentation.
 */
public class JdkOllamaClient implements OllamaClient {

    private final ObjectMapper objectMapper;
    private final Options options;

    public JdkOllamaClient(Options options) {
        this.options = options;
        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public List<ModelInfo> localModels() {
        String serverUrl = String.format("http://%s:%d/api/tags", options.host(), options.port());
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(new URI(serverUrl))
                    .GET()
                    .build();

            HttpResponse<String> httpResponse = HttpClient.newHttpClient().send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() != 200) {
                throw new RuntimeException(
                        "Unexpected response code: " + httpResponse.statusCode() + " response body: "
                                + httpResponse.body());
            }

            return objectMapper.readValue(httpResponse.body(), ModelsResponse.class).models();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to convert " + serverUrl + " to URI", e);
        } catch (ConnectException e) {
            throw new OllamaClient.ServerUnavailableException(options.host(), options.port());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ModelInfo modelInfo(String modelName) {
        String serverUrl = String.format("http://%s:%d/api/show", options.host(), options.port());
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(new URI(serverUrl))
                    .POST(HttpRequest.BodyPublishers.ofString(String.format("{\"name\":\"%s\"}", modelName)))
                    .build();

            HttpResponse<String> httpResponse = HttpClient.newHttpClient().send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() != 200) {
                if (httpResponse.statusCode() == 404) {
                    throw new OllamaClient.ModelNotFoundException(modelName);
                }
                throw new RuntimeException(
                        "Unexpected response code: " + httpResponse.statusCode() + " response body: " + httpResponse.body());
            }

            return objectMapper.readValue(httpResponse.body(), ModelInfo.class);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to convert " + serverUrl + " to URI", e);
        } catch (ConnectException e) {
            throw new OllamaClient.ServerUnavailableException(options.host(), options.port());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Flow.Publisher<PullAsyncLine> pullAsync(String modelName) {
        String serverUrl = String.format("http://%s:%d/api/pull", options.host(), options.port());
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(new URI(serverUrl))
                    .POST(HttpRequest.BodyPublishers.ofString(String.format("{\"name\":\"%s\", \"stream\": true}", modelName)))
                    .build();

            // can't use Multi.createFrom().emitter because it causes ClassLoader issues in the tests
            return new EmitterBasedMulti<>(emitter -> {
                try {
                    HttpClient.newHttpClient().send(httpRequest,
                            HttpResponse.BodyHandlers
                                    .fromLineSubscriber(new PullAsyncLineSubscriber(emitter, objectMapper, modelName)));
                } catch (ConnectException e) {
                    throw new OllamaClient.ServerUnavailableException(options.host(), options.port());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, BackPressureStrategy.BUFFER);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to convert " + serverUrl + " to URI", e);
        }
    }

    private record PullAsyncLineSubscriber(MultiEmitter<? super PullAsyncLine> emitter, ObjectMapper objectMapper,
            String modelName) implements Flow.Subscriber<String> {

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String item) {
            if (item.isBlank()) {
                return;
            }
            if (item.contains("file does not exist")) {
                emitter.fail(new ModelDoesNotExistException(modelName));
            }
            try {
                emitter.emit(objectMapper.readValue(item, PullAsyncLine.class));
            } catch (Exception e) {
                emitter.fail(e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            emitter.fail(throwable);
        }

        @Override
        public void onComplete() {
            emitter.complete();
        }
    }

    private record ModelsResponse(List<ModelInfo> models) {

    }
}
