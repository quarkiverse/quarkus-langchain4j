package io.quarkiverse.langchain4j.anthropic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

class ModelAuthProviderSmokeTest extends AnthropicSmokeTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

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
    void testModelAuthProviderUsed() {
        assertThat(ClientProxy.unwrap(chatModel)).isInstanceOf(AnthropicChatModel.class);
        assertThat(ClientProxy.unwrap(streamingChatModel)).isInstanceOf(AnthropicStreamingChatModel.class);

        mock.chat().reply(ChatReply.text("Hello!").usage(14, 5));

        String response = chatModel.chat("hello");
        assertThat(response).isEqualTo("Hello!");

        assertThat(mock.requests()).hasSize(1);

        var loggedRequest = mock.chat().singleRequest();
        assertThat(loggedRequest.path()).isEqualTo("/anthropic/v1/messages");
        assertThat(loggedRequest.header("Authorization")).isEqualTo("Bearer token");
        assertThat(loggedRequest.header("x-api-key")).isNull();
        assertThat(loggedRequest.header("anthropic-version")).isNotNull();
    }

    @ApplicationScoped
    public static class DummyModelAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer token";
        }
    }
}
