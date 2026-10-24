package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The chat model beans declare customizer injection points for both APIs. In Responses mode the customizers of the
 * Responses builders must be applied, while a customizer of the Chat Completions builder must be ignored.
 */
public class ResponsesModelBuilderCustomizerTest extends OpenAiBaseTest {

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
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @BeforeEach
    void setUp() {
        resetMappings();
        resetRequests();
    }

    @Test
    void responsesCustomizerIsAppliedToBlockingModel() {
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "resp_1",
                                  "model": "gpt-6-luna",
                                  "status": "completed",
                                  "output": [
                                    {
                                      "type": "message",
                                      "role": "assistant",
                                      "content": [ { "type": "output_text", "text": "hello" } ]
                                    }
                                  ],
                                  "usage": { "input_tokens": 1, "output_tokens": 2, "total_tokens": 3 }
                                }
                                """)));

        assertThat(chatModel.chat("test")).isEqualTo("hello");

        LoggedRequest request = singleLoggedRequest();
        assertThat(request.getHeader("X-Responses-Customizer")).isEqualTo("applied");
        assertThat(request.containsHeader("X-Completions-Customizer")).isFalse();
        assertThat(request.containsHeader("X-Streaming-Responses-Customizer")).isFalse();
    }

    @Test
    void responsesCustomizerIsAppliedToStreamingModel() throws InterruptedException {
        var eventStream = """
                data: {"type":"response.output_text.delta","delta":"hello"}

                data: {"type":"response.completed","response":{"id":"resp_1","model":"gpt-6-luna","status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"hello"}]}],"usage":{"input_tokens":1,"output_tokens":2,"total_tokens":3}}}

                data: [DONE]
                """;
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));

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

        LoggedRequest request = singleLoggedRequest();
        assertThat(request.getHeader("X-Streaming-Responses-Customizer")).isEqualTo("applied");
        assertThat(request.containsHeader("X-Completions-Customizer")).isFalse();
        assertThat(request.containsHeader("X-Responses-Customizer")).isFalse();
    }
}
