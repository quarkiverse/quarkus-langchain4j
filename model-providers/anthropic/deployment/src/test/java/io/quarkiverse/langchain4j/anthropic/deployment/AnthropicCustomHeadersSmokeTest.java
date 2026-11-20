package io.quarkiverse.langchain4j.anthropic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A gateway in front of Anthropic authenticates on its own header, so {@code customHeaders} passed
 * to the model builder has to reach the request. The client built by the extension is the one the
 * models actually use, so this covers the Quarkus substitution rather than langchain4j's own
 * client.
 */
class AnthropicCustomHeadersSmokeTest extends AnthropicSmokeTest {

    private static final String GATEWAY_HEADER = "X-API-Gateway-Key";
    private static final String GATEWAY_VALUE = "gateway-secret";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY);

    // the mock points the configured base URL at itself
    @ConfigProperty(name = "quarkus.langchain4j.anthropic.base-url")
    String baseUrl;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void customHeadersReachBlockingRequests() {
        mock.chat().reply(ChatReply.text("Hello!").usage(14, 5));

        AnthropicChatModel model = AnthropicChatModel.builder()
                .apiKey(API_KEY)
                .baseUrl(baseUrl)
                .customHeaders(Map.of(GATEWAY_HEADER, GATEWAY_VALUE))
                .build();

        assertThat(model.chat("hello")).isEqualTo("Hello!");

        var request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/anthropic/v1/messages");
        assertThat(request.header(GATEWAY_HEADER)).isEqualTo(GATEWAY_VALUE);
        // The provider credential still travels on its own header.
        assertThat(request.header("x-api-key")).isEqualTo(API_KEY);
    }

    @Test
    void customHeadersReachStreamingRequests() {
        mock.chat().reply(ChatReply.text("Hello!").usage(10, 20));

        AnthropicStreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(API_KEY)
                .baseUrl(baseUrl)
                .customHeaders(Map.of(GATEWAY_HEADER, GATEWAY_VALUE))
                .build();

        var response = new AtomicReference<ChatResponse>();
        model.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onError(Throwable error) {
                fail("Streaming failed: %s".formatted(error.getMessage()), error);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.set(completeResponse);
            }
        });

        await().atMost(Duration.ofSeconds(10)).until(() -> response.get() != null);
        assertThat(response.get().aiMessage().text()).isEqualTo("Hello!");

        var request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/anthropic/v1/messages");
        assertThat(request.header(GATEWAY_HEADER)).isEqualTo(GATEWAY_VALUE);
        assertThat(request.header("x-api-key")).isEqualTo(API_KEY);
    }
}
