package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The chat model beans declare customizer injection points for both APIs. In Responses mode the customizers of the
 * Responses builders must be applied, while a customizer of the Chat Completions builder must be ignored.
 */
public class ResponsesModelBuilderCustomizerTest {

    @ApplicationScoped
    public static class ResponsesCustomizer implements ModelBuilderCustomizer<OpenAiResponsesChatModel.Builder> {

        @Override
        public void customize(OpenAiResponsesChatModel.Builder builder) {
            builder.customHeaders(Map.of("X-Responses-Customizer", "applied"));
        }
    }

    @ApplicationScoped
    public static class StreamingResponsesCustomizer
            implements ModelBuilderCustomizer<OpenAiResponsesStreamingChatModel.Builder> {

        @Override
        public void customize(OpenAiResponsesStreamingChatModel.Builder builder) {
            builder.customHeaders(Map.of("X-Streaming-Responses-Customizer", "applied"));
        }
    }

    @ApplicationScoped
    public static class CompletionsCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {

        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.customHeaders(Map.of("X-Completions-Customizer", "applied"));
        }
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ResponsesCustomizer.class, StreamingResponsesCustomizer.class, CompletionsCustomizer.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Test
    void responsesCustomizerIsAppliedToBlockingModel() {
        mock.chat().reply("hello");

        assertThat(chatModel.chat("test")).isEqualTo("hello");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/responses");
        assertThat(request.header("X-Responses-Customizer")).isEqualTo("applied");
        assertThat(request.header("X-Completions-Customizer")).isNull();
        assertThat(request.header("X-Streaming-Responses-Customizer")).isNull();
    }

    @Test
    void responsesCustomizerIsAppliedToStreamingModel() throws InterruptedException {
        mock.chat().reply("hello");

        var error = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        streamingChatModel.chat("test", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                latch.countDown();
            }
        });
        if (!latch.await(1, TimeUnit.MINUTES)) {
            fail("Streaming did not complete in time");
        }

        assertThat(error.get()).isNull();
        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/responses");
        assertThat(request.header("X-Streaming-Responses-Customizer")).isEqualTo("applied");
        assertThat(request.header("X-Completions-Customizer")).isNull();
        assertThat(request.header("X-Responses-Customizer")).isNull();
    }
}
