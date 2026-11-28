package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.awaitility.core.ThrowingRunnable;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceSpanAttributes;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;

/**
 * With {@code include-ai-service-result} enabled, the AI service span carries the result of the invocation: as-is for a
 * {@code String}, as JSON otherwise, and the streamed text for streaming methods.
 */
class AiServiceSpanResultTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addClasses(BlockingAssistant.class, StreamingAssistant.class, Answer.class,
                                    AnsweringChatModelSupplier.class, StreamingChatModelSupplier.class,
                                    InMemorySpanExporterProducer.class);
                }
            })
            .overrideRuntimeConfigKey("quarkus.otel.bsp.schedule.delay", "PT0.001S")
            .overrideRuntimeConfigKey("quarkus.otel.traces.sampler", "always_on")
            .overrideRuntimeConfigKey("quarkus.langchain4j.tracing.include-ai-service-result", "true");

    @Inject
    InMemorySpanExporter exporter;

    @Inject
    BlockingAssistant blockingAssistant;

    @Inject
    StreamingAssistant streamingAssistant;

    @BeforeEach
    void reset() {
        exporter.reset();
    }

    @Test
    void recordsAStringAsIs() {
        AnsweringChatModelSupplier.answer = "Paris";

        assertThat(blockingAssistant.text("Capital of France?"))
                .isEqualTo("Paris");
        assertThat(recordedResult())
                .isEqualTo("Paris");
    }

    @Test
    void recordsAnObjectAsJson() {
        AnsweringChatModelSupplier.answer = """
                {"text": "Paris", "confidence": 0.9}
                """;

        assertThat(blockingAssistant.structured("Capital of France?"))
                .isEqualTo(new Answer("Paris", 0.9));
        assertThat(recordedResult())
                .isEqualTo("""
                        {"text":"Paris","confidence":0.9}""");
    }

    @Test
    void recordsTheContentOfAResult() {
        AnsweringChatModelSupplier.answer = "Paris";

        assertThat(blockingAssistant.result("Capital of France?").content())
                .isEqualTo("Paris");
        assertThat(recordedResult())
                .isEqualTo("Paris");
    }

    @Test
    void recordsTheConcatenatedTextOfAStringStream() {
        streamingAssistant.stream("Capital of France?")
                .subscribe()
                .withSubscriber(AssertSubscriber.<String> create(Long.MAX_VALUE))
                .awaitCompletion(TIMEOUT)
                .assertItems("Pa", "ris");

        assertThat(recordedResult())
                .isEqualTo("Paris");
    }

    @Test
    void recordsTheFinalTextOfAnEventStream() {
        streamingAssistant.events("Capital of France?")
                .subscribe()
                .withSubscriber(AssertSubscriber.<ChatEvent> create(Long.MAX_VALUE))
                .awaitCompletion(TIMEOUT);

        assertThat(recordedResult())
                .isEqualTo("Paris");
    }

    @Test
    void recordsTheFinalTextOfATokenStream() throws Exception {
        var completion = new CompletableFuture<ChatResponse>();
        streamingAssistant.tokenStream("Capital of France?")
                .onPartialResponse(new Consumer<String>() {
                    @Override
                    public void accept(String ignored) {
                    }
                })
                .onCompleteResponse(new Consumer<ChatResponse>() {
                    @Override
                    public void accept(ChatResponse response) {
                        completion.complete(response);
                    }
                })
                .onError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable failure) {
                        completion.completeExceptionally(failure);
                    }
                })
                .start();
        completion.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        assertThat(recordedResult())
                .isEqualTo("Paris");
    }

    @Test
    void recordsTheFinalTextOfATokenStreamWhoseCancellationFailed() throws Exception {
        var completion = new CompletableFuture<ChatResponse>();
        streamingAssistant.tokenStream("Capital of France?")
                .onPartialResponseWithContext(new BiConsumer<PartialResponse, PartialResponseContext>() {
                    @Override
                    public void accept(PartialResponse ignored, PartialResponseContext context) {
                        try {
                            context.streamingHandle().cancel();
                        } catch (UnsupportedFeatureException ignoredFailure) {
                            // the provider cannot cancel, so the stream goes on to completion
                        }
                    }
                })
                .onCompleteResponse(new Consumer<ChatResponse>() {
                    @Override
                    public void accept(ChatResponse response) {
                        completion.complete(response);
                    }
                })
                .onError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable failure) {
                        completion.completeExceptionally(failure);
                    }
                })
                .start();

        assertThat(completion.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).aiMessage().text())
                .isEqualTo("Paris");
        assertThat(recordedResult())
                .isEqualTo("Paris");
    }

    private String recordedResult() {
        return awaitAiServiceSpan()
                .getAttributes()
                .get(AiServiceSpanAttributes.RESULT);
    }

    private List<SpanData> aiServiceSpans() {
        var spans = new ArrayList<SpanData>();
        for (SpanData span : exporter.getFinishedSpanItems()) {
            if (AiServiceSpanAttributes.INVOKE_AI_SERVICE
                    .equals(span.getAttributes().get(AiServiceSpanAttributes.GEN_AI_OPERATION_NAME))) {
                spans.add(span);
            }
        }
        return spans;
    }

    private SpanData awaitAiServiceSpan() {
        return awaitAiServiceSpans(1).get(0);
    }

    private List<SpanData> awaitAiServiceSpans(int count) {
        await().atMost(TIMEOUT)
                .untilAsserted(new ThrowingRunnable() {
                    @Override
                    public void run() {
                        assertThat(aiServiceSpans())
                                .hasSize(count);
                    }
                });
        return aiServiceSpans();
    }

    public record Answer(String text, double confidence) {
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = AnsweringChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface BlockingAssistant {

        String text(@UserMessage String question);

        Answer structured(@UserMessage String question);

        Result<String> result(@UserMessage String question);
    }

    @ApplicationScoped
    @RegisterAiService(streamingChatLanguageModelSupplier = StreamingChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface StreamingAssistant {

        Multi<String> stream(@UserMessage String question);

        Multi<ChatEvent> events(@UserMessage String question);

        TokenStream tokenStream(@UserMessage String question);
    }

    public static class AnsweringChatModelSupplier implements Supplier<ChatModel> {

        static volatile String answer;

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from(answer))
                            .build();
                }
            };
        }
    }

    /**
     * Streams through a provider that cannot cancel, like one whose handle throws {@link UnsupportedFeatureException}.
     */
    public static class StreamingChatModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new StreamingChatModel() {
                @Override
                public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                    var context = new PartialResponseContext(new StreamingHandle() {
                        @Override
                        public void cancel() {
                            throw new UnsupportedFeatureException("Cancellation is not supported");
                        }

                        @Override
                        public boolean isCancelled() {
                            return false;
                        }
                    });
                    handler.onPartialResponse(new PartialResponse("Pa"), context);
                    handler.onPartialResponse(new PartialResponse("ris"), context);
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from("Paris"))
                            .build());
                }
            };
        }
    }

    public static class InMemorySpanExporterProducer {

        @ApplicationScoped
        InMemorySpanExporter exporter() {
            return InMemorySpanExporter.create();
        }
    }
}
