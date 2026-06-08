package io.quarkiverse.langchain4j.mistralai.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

class MistralAiStreamingChatLanguageModelSmokeTest extends WiremockAware {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "mistral-tiny";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void streaming() throws InterruptedException {
        assertThat(ClientProxy.unwrap(streamingChatModel))
                .isInstanceOf(MistralAiStreamingChatModel.class);

        // Mistral emits a tool-call delta with "content": null. Before the null-guard fix,
        // QuarkusMistralAiClient iterated getDelta().getContent() and threw a NullPointerException,
        // breaking streaming + function calling. This stream reproduces that exact shape.
        String eventStream = """
                data: {"id":"cmpl-1","object":"chat.completion.chunk","created":1711442725,"model":"mistral-tiny","choices":[{"index":0,"delta":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"get_weather","arguments":"{}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":19,"total_tokens":27,"completion_tokens":8}}

                data: [DONE]

                """;

        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));

        AtomicReference<ChatResponse> response = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        streamingChatModel.chat("What is the weather?", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.set(completeResponse);
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }
        });

        assertThat(latch.await(30, TimeUnit.SECONDS))
                .as("streaming did not complete within the timeout")
                .isTrue();

        // The regression guard: a null-content delta must not blow up the stream.
        if (error.get() != null) {
            fail("streaming failed: %s".formatted(error.get().getMessage()), error.get());
        }

        // The stream completed without onError -> the null-content delta no longer NPEs.
        assertThat(response.get()).isNotNull();
        assertThat(response.get().aiMessage()).isNotNull();

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("Quarkus REST Client");
        String requestBody = new String(loggedRequest.getBody());
        assertThat(requestBody).contains(CHAT_MODEL_ID).contains("stream");
    }
}
