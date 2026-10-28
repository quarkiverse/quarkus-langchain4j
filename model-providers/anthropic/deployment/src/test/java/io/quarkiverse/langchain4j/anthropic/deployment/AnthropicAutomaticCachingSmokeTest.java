package io.quarkiverse.langchain4j.anthropic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

class AnthropicAutomaticCachingSmokeTest extends AnthropicSmokeTest {

    private static final String CACHE_CONTROL = """
            { "type" : "ephemeral", "ttl" : "1h" }""";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.cache-automatically", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.chat-model.cache-ttl", "1h");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void blockingRequestEnablesAutomaticCaching() throws Exception {
        mock.chat().reply(ChatReply.text("Hi!").usage(10, 2));

        assertThat(chatModel.chat("Hello")).isEqualTo("Hi!");

        JsonNode body = mock.chat().singleRequest().bodyAsJson();
        assertThat(body.get("cache_control")).isEqualTo(MAPPER.readTree(CACHE_CONTROL));
    }

    @Test
    void streamingRequestEnablesAutomaticCaching() throws Exception {
        mock.chat().reply(ChatReply.text("Hi!").usage(10, 2));

        var response = new AtomicReference<ChatResponse>();
        streamingChatModel.chat("Hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.set(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                response.set(null);
            }
        });

        await().atMost(Duration.ofSeconds(10)).until(() -> response.get() != null);
        assertThat(response.get().aiMessage().text()).isEqualTo("Hi!");

        JsonNode body = mock.chat().singleRequest().bodyAsJson();
        assertThat(body.get("stream").booleanValue()).isTrue();
        assertThat(body.get("cache_control")).isEqualTo(MAPPER.readTree(CACHE_CONTROL));
    }
}
