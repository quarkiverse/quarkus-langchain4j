package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.awaitility.core.ThrowingRunnable;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceSpanAttributes;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceSpanCompleteContext;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceSpanContributor;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceSpanErrorContext;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceSpanStartContext;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * An {@link AiServiceSpanContributor} is told about every AI service invocation and can add its own data to the span.
 * A contributor that throws never fails the invocation.
 */
class AiServiceSpanContributorTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final AttributeKey<String> STARTED = AttributeKey.stringKey("test.started");
    private static final AttributeKey<String> COMPLETED = AttributeKey.stringKey("test.completed");
    private static final AttributeKey<String> FAILED = AttributeKey.stringKey("test.failed");

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addClasses(Assistant.class, AnsweringChatModelSupplier.class, RecordingContributor.class,
                                    FailingContributor.class,
                                    InMemorySpanExporterProducer.class);
                }
            })
            .overrideRuntimeConfigKey("quarkus.otel.bsp.schedule.delay", "PT0.001S")
            .overrideRuntimeConfigKey("quarkus.otel.traces.sampler", "always_on");

    @Inject
    InMemorySpanExporter exporter;

    @Inject
    Assistant assistant;

    @BeforeEach
    void reset() {
        exporter.reset();
        AnsweringChatModelSupplier.failure = null;
    }

    @Test
    void contributorSeesTheInvocationAndItsResult() {
        assertThat(assistant.chat("hello", "memory-1"))
                .isEqualTo("answer");

        SpanData span = awaitAiServiceSpan();
        assertThat(span.getAttributes().asMap())
                .containsEntry(STARTED, Assistant.class.getName() + "#chat[hello, memory-1]")
                .containsEntry(COMPLETED, "answer")
                .doesNotContainKey(FAILED);
        // the failing contributor is recorded on the span on start and on completion, and the call still succeeds
        assertThat(span.getEvents())
                .hasSize(2)
                .extracting("exception")
                .extracting("message")
                .containsOnly("--contributor failed--");
    }

    @Test
    void contributorSeesTheFailure() {
        AnsweringChatModelSupplier.failure = new IllegalStateException("--model failed--");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(new ThrowingCallable() {
                    @Override
                    public void call() {
                        assistant.chat("hello", "memory-1");
                    }
                })
                .withMessage("--model failed--");

        assertThat(awaitAiServiceSpan().getAttributes().asMap())
                .containsEntry(FAILED, "--model failed--")
                .doesNotContainKey(COMPLETED);
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

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = AnsweringChatModelSupplier.class)
    public interface Assistant {

        String chat(@UserMessage String message, @MemoryId String memoryId);
    }

    @ApplicationScoped
    public static class RecordingContributor implements AiServiceSpanContributor {

        @Override
        public void onStart(AiServiceSpanStartContext context, Span span) {
            span.setAttribute(STARTED, "%s#%s%s".formatted(context.interfaceName(), context.methodName(),
                    context.methodArguments()));
        }

        @Override
        public void onComplete(AiServiceSpanCompleteContext context, Span span) {
            span.setAttribute(COMPLETED, String.valueOf(context.result().orElse("<none>")));
        }

        @Override
        public void onError(AiServiceSpanErrorContext context, Span span) {
            span.setAttribute(FAILED, context.error().getMessage());
        }
    }

    @ApplicationScoped
    public static class FailingContributor implements AiServiceSpanContributor {

        @Override
        public void onStart(AiServiceSpanStartContext context, Span span) {
            throw new IllegalStateException("--contributor failed--");
        }

        @Override
        public void onComplete(AiServiceSpanCompleteContext context, Span span) {
            throw new IllegalStateException("--contributor failed--");
        }
    }

    public static class AnsweringChatModelSupplier implements Supplier<ChatModel> {

        static volatile RuntimeException failure;

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    if (failure != null) {
                        throw failure;
                    }
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from("answer"))
                            .build();
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
