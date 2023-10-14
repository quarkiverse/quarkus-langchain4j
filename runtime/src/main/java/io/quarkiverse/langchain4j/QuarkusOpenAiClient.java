package io.quarkiverse.langchain4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;

/**
 * Implements feature set of {@link OpenAiClient} using Quarkus functionality
 */
public class QuarkusOpenAiClient extends OpenAiClient {

    private final String token;

    private final OpenAiQuarkusRestApi restApi;

    public QuarkusOpenAiClient(String apiKey) {
        this(new Builder().openAiApiKey(apiKey));
    }

    private QuarkusOpenAiClient(Builder serviceBuilder) {
        this.token = serviceBuilder.openAiApiKey;

        try {
            restApi = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(new URI(serviceBuilder.baseUrl))
                    // TODO add the rest of the relevant configuration
                    .build(OpenAiQuarkusRestApi.class);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SyncOrAsyncOrStreaming<CompletionResponse> completion(CompletionRequest request) {
        throw new NotImplementedYet();
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
                return restApi.blockingCreateChatCompletion(request, token);
            }

            @Override
            public AsyncResponseHandling onResponse(Consumer<ChatCompletionResponse> responseHandler) {
                return new AsyncResponseHandlingImpl<>(
                        new Supplier<>() {
                            @Override
                            public Uni<ChatCompletionResponse> get() {
                                return restApi.createChatCompletion(request, token);
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
                                return restApi.streamingCreateChatCompletion(request, token);
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
                return restApi.blockingCreateChatCompletion(request, token).content();
            }

            @Override
            public AsyncResponseHandling onResponse(Consumer<String> responseHandler) {
                return new AsyncResponseHandlingImpl<>(
                        new Supplier<>() {
                            @Override
                            public Uni<String> get() {
                                return restApi.createChatCompletion(request, token).map(ChatCompletionResponse::content);
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
                                return restApi.streamingCreateChatCompletion(request, token)
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
        throw new NotImplementedYet();
    }

    @Override
    public SyncOrAsync<List<Float>> embedding(String input) {
        throw new NotImplementedYet();
    }

    @Override
    public SyncOrAsync<ModerationResponse> moderation(ModerationRequest request) {
        throw new NotImplementedYet();
    }

    @Override
    public SyncOrAsync<ModerationResult> moderation(String input) {
        throw new NotImplementedYet();
    }

    @Override
    public void shutdown() {

    }

    public static class QuarkusOpenAiClientBuilderFactory implements OpenAiClientBuilderFactory {

        @Override
        public Builder get() {
            ArcContainer arcContainer = Arc.container();
            if (arcContainer == null) {
                // in regular unit tests Quarkus is not running, so don't do anything
                return null;
            }
            return new Builder();
        }
    }

    public static class Builder extends OpenAiClient.Builder<QuarkusOpenAiClient, Builder> {

        @Override
        public QuarkusOpenAiClient build() {
            return new QuarkusOpenAiClient(this);
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
