package io.quarkiverse.langchain4j.vertexai.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.internal.MockVertexAiAuthProvider;
import io.quarkiverse.langchain4j.vertexai.runtime.VertxAiRestApi;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiRoundTripTest {

    // no project-id, location or Google credentials are configured
    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "vertexai");

    @Inject
    ChatModel chatModel;

    @Inject
    VertxAiRestApi.AuthProvider authProvider;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void textReplyWithPlaceholderIdentityAndMockToken() {
        mock.chat().reply("Nice to meet you");

        assertThat(chatModel.chat("hello")).isEqualTo("Nice to meet you");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path())
                .isEqualTo(
                        "/vertexai/v1/projects/mock-project/locations/us-central1/publishers/google/models/chat-bison:predict");
        assertThat(r.dialect()).isEqualTo("vertexai");
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-access-token");
        assertThat(r.asChat().modelName()).isEqualTo("chat-bison");
        assertThat(r.asChat().messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("hello");
        });
    }

    @Test
    void theMockTokenProviderReplacesApplicationDefaultCredentials() {
        assertThat(authProvider).isInstanceOf(MockVertexAiAuthProvider.class);
    }

    @Test
    void usageAndSystemContext() {
        mock.chat().reply(ChatReply.text("x").usage(11, 37));

        ChatResponse r = chatModel.chat(ChatRequest.builder()
                .messages(SystemMessage.from("be brief"), UserMessage.from("hi")).build());

        assertThat(r.aiMessage().text()).isEqualTo("x");
        assertThat(r.tokenUsage().inputTokenCount()).isEqualTo(11);
        assertThat(r.tokenUsage().outputTokenCount()).isEqualTo(37);
        assertThat(mock.chat().singleRequest().asChat().messages()).extracting(m -> m.role() + ":" + m.text())
                .containsExactly("system:be brief", "user:hi");
    }

    @Test
    void errorSurfacesAsException() {
        mock.chat().fail(400, "bad prompt");

        assertThatThrownBy(() -> chatModel.chat("hi")).hasMessageContaining("400");
        // the client does not retry
        assertThat(mock.chat().requests()).hasSize(1);
    }
}
