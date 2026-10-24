package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

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
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * HTTP errors from {@code /v1/responses} must surface as the LangChain4j exception matching the status code, with the
 * provider's message, for both the blocking and the streaming model.
 */
public class ResponsesErrorMappingTest {

    private static final String TOOLS_WITH_REASONING_MESSAGE = "Function tools with reasoning_effort are not supported for gpt-6-luna in /v1/chat/completions. To use function tools, use /v1/responses or set reasoning_effort to 'none'.";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
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
    void badRequestIsMappedToInvalidRequestException() {
        mock.chat().fail(400, TOOLS_WITH_REASONING_MESSAGE);

        assertThatThrownBy(() -> chatModel.chat("hi"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Function tools with reasoning_effort are not supported");
        assertResponsesRequests();
    }

    @Test
    void unauthorizedIsMappedToAuthenticationException() {
        mock.chat().fail(401, "Incorrect API key provided");

        assertThatThrownBy(() -> chatModel.chat("hi"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Incorrect API key provided");
        assertResponsesRequests();
    }

    @Test
    void streamingErrorReachesTheHandler() throws InterruptedException {
        mock.chat().fail(400, TOOLS_WITH_REASONING_MESSAGE);

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
        assertResponsesRequests();
    }

    private void assertResponsesRequests() {
        assertThat(mock.requests()).isNotEmpty()
                .allSatisfy(request -> assertThat(request.path()).isEqualTo("/v1/responses"));
    }
}
