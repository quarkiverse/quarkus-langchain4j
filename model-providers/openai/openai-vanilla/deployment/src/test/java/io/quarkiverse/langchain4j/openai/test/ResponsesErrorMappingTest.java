package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * HTTP errors from {@code /v1/responses} must surface as the LangChain4j exception matching the status code, with the
 * provider's message, for both the blocking and the streaming model.
 */
public class ResponsesErrorMappingTest extends OpenAiBaseTest {

    private static final String TOOLS_WITH_REASONING_ERROR = """
            {
              "error": {
                "message": "Function tools with reasoning_effort are not supported for gpt-6-luna in /v1/chat/completions. To use function tools, use /v1/responses or set reasoning_effort to 'none'.",
                "type": "invalid_request_error",
                "param": "reasoning_effort",
                "code": null
              }
            }
            """;

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
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
    void badRequestIsMappedToInvalidRequestException() {
        stubError(400, TOOLS_WITH_REASONING_ERROR);

        assertThatThrownBy(() -> chatModel.chat("hi"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Function tools with reasoning_effort are not supported");
    }

    @Test
    void unauthorizedIsMappedToAuthenticationException() {
        stubError(401, """
                {"error": {"message": "Incorrect API key provided", "type": "invalid_request_error", "code": "invalid_api_key"}}
                """);

        assertThatThrownBy(() -> chatModel.chat("hi"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Incorrect API key provided");
    }

    @Test
    void streamingErrorReachesTheHandler() throws InterruptedException {
        stubError(400, TOOLS_WITH_REASONING_ERROR);

        var error = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        streamingChatModel.chat("hi", new StreamingChatResponseHandler() {
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

        assertThat(error.get())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Function tools with reasoning_effort are not supported");
    }

    private void stubError(int status, String body) {
        wiremock().register(post(urlEqualTo("/v1/responses"))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
