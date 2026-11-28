package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.awaitility.core.ThrowingRunnable;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.UserName;
import dev.langchain4j.service.V;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.ImageUrl;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceSpanAttributes;
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * With {@code include-ai-service-arguments} enabled, the AI service span carries the call data of the invocation,
 * keyed by parameter name, and leaves out the parameters the framework consumes itself.
 */
class AiServiceSpanArgumentsTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addClasses(ClaimAssistant.class, Claim.class, SelfReferencing.class,
                                    AnsweringChatModelSupplier.class,
                                    InMemorySpanExporterProducer.class);
                }
            })
            .overrideRuntimeConfigKey("quarkus.otel.bsp.schedule.delay", "PT0.001S")
            .overrideRuntimeConfigKey("quarkus.otel.traces.sampler", "always_on")
            .overrideRuntimeConfigKey("quarkus.langchain4j.tracing.include-ai-service-arguments", "true");

    @Inject
    InMemorySpanExporter exporter;

    @Inject
    ClaimAssistant assistant;

    @BeforeEach
    void reset() {
        exporter.reset();
    }

    @Test
    void recordsTheCallDataAndLeavesOutTheManagedParameters() {
        assistant.answer("How much was paid?", new Claim("C-1", 12.5), "memory-1",
                InvocationParameters.from("tenant", "acme"), "Alice",
                ChatRequestParameters.builder().temperature(0.1).build(), null, "https://example.com/cat.png");

        assertThat(recordedArguments())
                .isEqualTo(json("""
                        {"question": "How much was paid?", "claim": {"id": "C-1", "amount": 12.5}}
                        """));
    }

    @Test
    void leavesOutContentAndMediaParametersDetectedByTheirType() {
        assistant.withAttachments("Describe them", Image.builder().url("https://example.com/cat.png").build(),
                List.<Content> of(TextContent.from("an extra note")), "detailed");

        assertThat(recordedArguments())
                .isEqualTo(json("""
                        {"message": "Describe them", "style": "detailed"}
                        """));
    }

    @Test
    void recordsAnUnserializableArgumentAsAMarkerAndKeepsTheOthers() {
        assertThat(assistant.withUnserializable("hello", new SelfReferencing(), 3))
                .isEqualTo("answer");

        assertThat(recordedArguments())
                .isEqualTo(json("""
                        {
                          "message": "hello",
                          "node": {"_unserializable": "%s"},
                          "count": 3
                        }
                        """.formatted(SelfReferencing.class.getName())));
    }

    @Test
    void recordsANullArgumentAsNull() {
        assistant.withNote("hello", null);

        assertThat(recordedArguments())
                .isEqualTo(json("""
                        {"message": "hello", "note": null}
                        """));
    }

    private JsonNode recordedArguments() {
        return json(awaitAiServiceSpan()
                .getAttributes()
                .get(AiServiceSpanAttributes.ARGUMENTS));
    }

    private static JsonNode json(String json) {
        return JSON.readTree(json);
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

    public record Claim(String id, double amount) {
    }

    /**
     * Jackson refuses to serialize an object that refers to itself.
     */
    public static class SelfReferencing {
        final SelfReferencing self = this;
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = AnsweringChatModelSupplier.class)
    public interface ClaimAssistant {

        @UserMessage("Answer {question} about claim {claim.id}")
        String answer(@V("question") String question, Claim claim, @MemoryId String memoryId,
                InvocationParameters invocationParameters, @UserName String userName,
                ChatRequestParameters requestParameters, @ModelName String modelName, @ImageUrl String imageUrl);

        String withUnserializable(@UserMessage String message, @V("node") SelfReferencing node, @V("count") int count);

        String withNote(@UserMessage String message, @V("note") String note);

        String withAttachments(@UserMessage String message, Image image, List<Content> attachments,
                @V("style") String style);
    }

    public static class AnsweringChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
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
