package io.quarkiverse.langchain4j.openai;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.resteasy.reactive.client.api.LoggingScope;
import org.jboss.resteasy.reactive.common.NotImplementedYet;

import dev.ai4j.openai4j.AsyncResponseHandling;
import dev.ai4j.openai4j.ErrorHandling;
import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.ResponseHandle;
import dev.ai4j.openai4j.StreamingCompletionHandling;
import dev.ai4j.openai4j.StreamingResponseHandling;
import dev.ai4j.openai4j.SyncOrAsync;
import dev.ai4j.openai4j.SyncOrAsyncOrStreaming;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.chat.Delta;
import dev.ai4j.openai4j.completion.CompletionRequest;
import dev.ai4j.openai4j.completion.CompletionResponse;
import dev.ai4j.openai4j.embedding.EmbeddingRequest;
import dev.ai4j.openai4j.embedding.EmbeddingResponse;
import dev.ai4j.openai4j.moderation.ModerationRequest;
import dev.ai4j.openai4j.moderation.ModerationResponse;
import dev.ai4j.openai4j.moderation.ModerationResult;
import dev.ai4j.openai4j.spi.OpenAiClientBuilderFactory;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;

/**
 * Implements feature set of {@link OpenAiClient} using Quarkus functionality
 */
public class QuarkusOpenAiClient extends OpenAiClient {

    private final String apiKey;
    private final String apiVersion;

    private final OpenAiRestApi restApi;

    private static final Map<Builder, OpenAiRestApi> cache = new ConcurrentHashMap<>();

    public QuarkusOpenAiClient(String apiKey) {
        this(new Builder().openAiApiKey(apiKey));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static void clearCache() {
        cache.clear();
    }

    private QuarkusOpenAiClient(Builder builder) {
        this.apiKey = determineApiKey(builder);
        this.apiVersion = builder.apiVersion;
        // cache the client the builder could be called with the same parameters from multiple models
        this.restApi = cache.compute(builder, new BiFunction<Builder, OpenAiRestApi, OpenAiRestApi>() {
            @Override
            public OpenAiRestApi apply(Builder builder, OpenAiRestApi openAiRestApi) {
                try {
                    QuarkusRestClientBuilder restApiBuilder = QuarkusRestClientBuilder.newBuilder()
                            .baseUri(new URI(builder.baseUrl))
                            .connectTimeout(builder.connectTimeout.toSeconds(), TimeUnit.SECONDS)
                            .readTimeout(builder.readTimeout.toSeconds(), TimeUnit.SECONDS);
                    if (builder.logRequests || builder.logResponses) {
                        restApiBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                        restApiBuilder.clientLogger(new OpenAiRestApi.OpenAiClientLogger(builder.logRequests,
                                builder.logResponses));
                    }
                    if (builder.proxy != null) {
                        if (builder.proxy.type() != Proxy.Type.HTTP) {
                            throw new IllegalArgumentException("Only HTTP type proxy is supported");
                        }
                        if (!(builder.proxy.address() instanceof InetSocketAddress)) {
                            throw new IllegalArgumentException("Unsupported proxy type");
                        }
                        InetSocketAddress socketAddress = (InetSocketAddress) builder.proxy.address();
                        restApiBuilder.proxyAddress(socketAddress.getHostName(), socketAddress.getPort());
                    }
                    return restApiBuilder.build(OpenAiRestApi.class);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });

    }

    private static String determineApiKey(Builder builder) {
        var result = builder.openAiApiKey;
        if (result != null) {
            return result;
        }
        result = builder.azureApiKey;
        return result;
    }

    @Override
    public SyncOrAsyncOrStreaming<CompletionResponse> completion(CompletionRequest request) {
        return new SyncOrAsyncOrStreaming<>() {
            @Override
            public CompletionResponse execute() {
                return restApi.blockingCompletion(CompletionRequest.builder().from(request).stream(null).build(),
                        apiKey, apiVersion);
            }

            @Override
            public AsyncResponseHandling onResponse(Consumer<CompletionResponse> responseHandler) {
                return new AsyncResponseHandlingImpl<>(
                        new Supplier<>() {
                            @Override
                            public Uni<CompletionResponse> get() {
                                return restApi.completion(request, apiKey, apiVersion);
                            }
                        },
                        responseHandler);
            }

            @Override
            public StreamingResponseHandling onPartialResponse(
                    Consumer<CompletionResponse> partialResponseHandler) {
                return new StreamingResponseHandlingImpl<>(
                        new Supplier<>() {
                            @Override
                            public Multi<CompletionResponse> get() {
                                return restApi.streamingCompletion(request, apiKey, apiVersion);
                            }
                        }, partialResponseHandler);
            }
        };
    }

    @Override
    public SyncOrAsyncOrStreaming<String> completion(String prompt) {
        throw new NotImplementedYet();
    }

    @Override
    public SyncOrAsyncOrStreaming<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request) {
        return new SyncOrAsyncOrStreaming<>() {
            @Override
            public ChatCompletionResponse execute() {
                return restApi.blockingChatCompletion(ChatCompletionRequest.builder().from(request).stream(null).build(),
                        apiKey, apiVersion);
            }

            @Override
            public AsyncResponseHandling onResponse(Consumer<ChatCompletionResponse> responseHandler) {
                return new AsyncResponseHandlingImpl<>(
                        new Supplier<>() {
                            @Override
                            public Uni<ChatCompletionResponse> get() {
                                return restApi.createChatCompletion(request, apiKey, apiVersion);
                            }
                        },
                        responseHandler);
            }

            @Override
            public StreamingResponseHandling onPartialResponse(
                    Consumer<ChatCompletionResponse> partialResponseHandler) {
                return new StreamingResponseHandlingImpl<>(
                        new Supplier<>() {
                            @Override
                            public Multi<ChatCompletionResponse> get() {
                                return restApi.streamingChatCompletion(request, apiKey, apiVersion);
                            }
                        }, partialResponseHandler);
            }
        };
    }

    @Override
    public SyncOrAsyncOrStreaming<String> chatCompletion(String userMessage) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .addUserMessage(userMessage)
                .build();

        return new SyncOrAsyncOrStreaming<>() {
            @Override
            public String execute() {
                return restApi
                        .blockingChatCompletion(request, apiKey, apiVersion)
                        .content();
            }

            @Override
            public AsyncResponseHandling onResponse(Consumer<String> responseHandler) {
                return new AsyncResponseHandlingImpl<>(
                        new Supplier<>() {
                            @Override
                            public Uni<String> get() {
                                return restApi
                                        .createChatCompletion(
                                                ChatCompletionRequest.builder().from(request).stream(null).build(),
                                                apiKey,
                                                apiVersion)
                                        .map(ChatCompletionResponse::content);
                            }
                        },
                        responseHandler);
            }

            @Override
            public StreamingResponseHandling onPartialResponse(
                    Consumer<String> partialResponseHandler) {
                return new StreamingResponseHandlingImpl<>(
                        new Supplier<>() {
                            @Override
                            public Multi<String> get() {
                                return restApi
                                        .streamingChatCompletion(
                                                ChatCompletionRequest.builder().from(request).stream(true).build(),
                                                apiKey, apiVersion)
                                        .filter(r -> {
                                            if (r.choices() != null) {
                                                if (r.choices().size() == 1) {
                                                    Delta delta = r.choices().get(0).delta();
                                                    if (delta != null) {
                                                        return delta.content() != null;
                                                    }
                                                }
                                            }
                                            return false;
                                        })
                                        .map(r -> r.choices().get(0).delta().content())
                                        .filter(Objects::nonNull);
                            }
                        }, partialResponseHandler);
            }
        };
    }

    @Override
    public SyncOrAsync<EmbeddingResponse> embedding(EmbeddingRequest request) {
        return new SyncOrAsync<>() {
            @Override
            public EmbeddingResponse execute() {
                return restApi.blockingEmbedding(request, apiKey, apiVersion);
            }

            @Override
            public AsyncResponseHandling onResponse(Consumer<EmbeddingResponse> responseHandler) {
                return new AsyncResponseHandlingImpl<>(
                        new Supplier<>() {
                            @Override
                            public Uni<EmbeddingResponse> get() {
                                return restApi.embedding(request, apiKey, apiVersion);
                            }
                        },
                        responseHandler);
            }
        };
    }

    @Override
    public SyncOrAsync<List<Float>> embedding(String input) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(input)
                .build();
        return new SyncOrAsync<>() {
            @Override
            public List<Float> execute() {
                return restApi.blockingEmbedding(request, apiKey, apiVersion).embedding();
            }

            @Override
            public AsyncResponseHandling onResponse(Consumer<List<Float>> responseHandler) {
                return new AsyncResponseHandlingImpl<>(
                        new Supplier<>() {
                            @Override
                            public Uni<List<Float>> get() {
                                return restApi.embedding(request, apiKey, apiVersion).map(EmbeddingResponse::embedding);
                            }
                        },
                        responseHandler);
            }
        };
    }

    @Override
    public SyncOrAsync<ModerationResponse> moderation(ModerationRequest request) {
        return new SyncOrAsync<>() {
            @Override
            public ModerationResponse execute() {
                return restApi.blockingModeration(request, apiKey, apiVersion);
            }

            @Override
            public AsyncResponseHandling onResponse(Consumer<ModerationResponse> responseHandler) {
                return new AsyncResponseHandlingImpl<>(
                        new Supplier<>() {
                            @Override
                            public Uni<ModerationResponse> get() {
                                return restApi.moderation(request, apiKey, apiVersion);
                            }
                        },
                        responseHandler);
            }
        };
    }

    @Override
    public SyncOrAsync<ModerationResult> moderation(String input) {
        ModerationRequest request = ModerationRequest.builder()
                .input(input)
                .build();

        return new SyncOrAsync<>() {
            @Override
            public ModerationResult execute() {
                return restApi.blockingModeration(request, apiKey, apiVersion).results().get(0);
            }

            @Override
            public AsyncResponseHandling onResponse(Consumer<ModerationResult> responseHandler) {
                return new AsyncResponseHandlingImpl<>(
                        new Supplier<>() {
                            @Override
                            public Uni<ModerationResult> get() {
                                return restApi.moderation(request, apiKey, apiVersion).map(r -> r.results().get(0));
                            }
                        },
                        responseHandler);
            }
        };
    }

    @Override
    public void shutdown() {

    }

    public static class QuarkusOpenAiClientBuilderFactory implements OpenAiClientBuilderFactory {

        @Override
        public Builder get() {
            return new Builder();
        }
    }

    public static class Builder extends OpenAiClient.Builder<QuarkusOpenAiClient, Builder> {

        @Override
        public QuarkusOpenAiClient build() {
            return new QuarkusOpenAiClient(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Builder builder = (Builder) o;
            return logRequests == builder.logRequests && logResponses == builder.logResponses
                    && logStreamingResponses == builder.logStreamingResponses && Objects.equals(baseUrl, builder.baseUrl)
                    && Objects.equals(apiVersion, builder.apiVersion) && Objects.equals(openAiApiKey,
                            builder.openAiApiKey)
                    && Objects.equals(azureApiKey, builder.azureApiKey) && Objects.equals(
                            callTimeout, builder.callTimeout)
                    && Objects.equals(connectTimeout, builder.connectTimeout)
                    && Objects.equals(readTimeout, builder.readTimeout) && Objects.equals(writeTimeout,
                            builder.writeTimeout)
                    && Objects.equals(proxy, builder.proxy);
        }

        @Override
        public int hashCode() {
            return Objects.hash(baseUrl, apiVersion, openAiApiKey, azureApiKey, callTimeout, connectTimeout, readTimeout,
                    writeTimeout, proxy, logRequests, logResponses, logStreamingResponses);
        }
    }

    private static class AsyncResponseHandlingImpl<RESPONSE> implements AsyncResponseHandling {
        private final AtomicReference<Consumer<Throwable>> errorHandlerRef = new AtomicReference<>(NoopErrorHandler.INSTANCE);

        private final SingleResultHandling<RESPONSE> resultHandling;

        public AsyncResponseHandlingImpl(Supplier<Uni<RESPONSE>> uniSupplier, Consumer<RESPONSE> responseHandler) {
            resultHandling = new SingleResultHandling<>(uniSupplier, responseHandler, errorHandlerRef);
        }

        @Override
        public ErrorHandling onError(Consumer<Throwable> errorHandler) {
            errorHandlerRef.set(errorHandler);
            return resultHandling;
        }

        @Override
        public ErrorHandling ignoreErrors() {
            errorHandlerRef.set(NoopErrorHandler.INSTANCE);
            return resultHandling;
        }

        private static class SingleResultHandling<RESPONSE> implements ErrorHandling {

            private final Supplier<Uni<RESPONSE>> uniSupplier;
            private final Consumer<RESPONSE> responseHandler;
            private final AtomicReference<Consumer<Throwable>> errorHandlerRef;

            public SingleResultHandling(Supplier<Uni<RESPONSE>> uniSupplier, Consumer<RESPONSE> responseHandler,
                    AtomicReference<Consumer<Throwable>> errorHandlerRef) {
                this.uniSupplier = uniSupplier;
                this.responseHandler = responseHandler;
                this.errorHandlerRef = errorHandlerRef;
            }

            @Override
            public ResponseHandle execute() {
                var cancellable = uniSupplier.get()
                        .subscribe().with(responseHandler, errorHandlerRef.get());
                return new ResponseHandleImpl(cancellable);
            }
        }
    }

    private static class StreamingResponseHandlingImpl<RESPONSE>
            implements StreamingResponseHandling, StreamingCompletionHandling {

        private final AtomicReference<Runnable> completeHandlerRef = new AtomicReference<>(NoopCompleteHandler.INSTANCE);
        private final AtomicReference<Consumer<Throwable>> errorHandlerRef = new AtomicReference<>(NoopErrorHandler.INSTANCE);

        private final StreamingResultErrorHandling<RESPONSE> resultHandling;

        public StreamingResponseHandlingImpl(Supplier<Multi<RESPONSE>> multiSupplier,
                Consumer<RESPONSE> partialResponseHandler) {
            resultHandling = new StreamingResultErrorHandling<>(multiSupplier, partialResponseHandler, completeHandlerRef,
                    errorHandlerRef);
        }

        @Override
        public StreamingCompletionHandling onComplete(Runnable streamingCompletionCallback) {
            completeHandlerRef.set(streamingCompletionCallback);
            return this;
        }

        @Override
        public ErrorHandling onError(Consumer<Throwable> errorHandler) {
            errorHandlerRef.set(errorHandler);
            return resultHandling;
        }

        @Override
        public ErrorHandling ignoreErrors() {
            errorHandlerRef.set(NoopErrorHandler.INSTANCE);
            return resultHandling;
        }

        private static class StreamingResultErrorHandling<RESPONSE> implements ErrorHandling {

            private final Supplier<Multi<RESPONSE>> multiSupplier;
            private final Consumer<RESPONSE> partialResponseHandler;
            private final AtomicReference<Runnable> completeHandlerRef;
            private final AtomicReference<Consumer<Throwable>> errorHandlerRef;

            public StreamingResultErrorHandling(Supplier<Multi<RESPONSE>> multiSupplier,
                    Consumer<RESPONSE> partialResponseHandler, AtomicReference<Runnable> completeHandlerRef,
                    AtomicReference<Consumer<Throwable>> errorHandlerRef) {
                this.multiSupplier = multiSupplier;
                this.partialResponseHandler = partialResponseHandler;
                this.completeHandlerRef = completeHandlerRef;
                this.errorHandlerRef = errorHandlerRef;
            }

            @Override
            public ResponseHandle execute() {
                var cancellable = multiSupplier.get()
                        .subscribe()
                        .with(partialResponseHandler, errorHandlerRef.get(), completeHandlerRef.get());
                return new ResponseHandleImpl(cancellable);
            }
        }
    }

    private static class NoopErrorHandler implements Consumer<Throwable> {

        private static final NoopErrorHandler INSTANCE = new NoopErrorHandler();

        private NoopErrorHandler() {
        }

        @Override
        public void accept(Throwable throwable) {

        }
    }

    private static class NoopCompleteHandler implements Runnable {

        private static final NoopCompleteHandler INSTANCE = new NoopCompleteHandler();

        private NoopCompleteHandler() {
        }

        @Override
        public void run() {

        }
    }

    private static class ResponseHandleImpl extends ResponseHandle {
        private final Cancellable cancellable;

        public ResponseHandleImpl(Cancellable cancellable) {
            this.cancellable = cancellable;
        }

        @Override
        public void cancel() {
            cancellable.cancel();
        }
    }

}
