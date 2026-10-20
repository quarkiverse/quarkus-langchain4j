package io.quarkiverse.langchain4j.bedrock.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BedrockChatModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestCredentialsProvider.class)
                    .addClass(CountingChatModelListener.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.model-id", "amazon.titan-text-express-v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.log-body", "true")
            .overrideRuntimeConfigKey("quarkus.log.category.\"io.quarkiverse.langchain4j.bedrock\".level", "DEBUG");

    @Inject
    ChatModel chatModel;

    @Inject
    CountingChatModelListener listener;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset_listener_counters() {
        listener.reset();
        mock.reset();
    }

    @Test
    void should_create_bedrock_model() {
        // given

        // when

        // then
        assertThat(ClientProxy.unwrap(chatModel)).isInstanceOf(BedrockChatModel.class);
    }

    @Test
    void should_answer_a_chat_message() {
        // given
        mock.chat().reply(ChatReply.text("Hello, I am good. How are you today?").usage(10, 21));

        // when
        var response = chatModel.chat("Hello, how are you today?");

        // then
        assertThat(response).isNotNull().isEqualTo("Hello, I am good. How are you today?");
        assertThat(listener.onRequestCount()).isEqualTo(1);
        assertThat(listener.onResponseCount()).isEqualTo(1);
        assertThat(listener.onErrorCount()).isEqualTo(0);
        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/bedrock/model/amazon.titan-text-express-v1/converse");
        assertThat(request.asChat().messages()).singleElement()
                .satisfies(m -> assertThat(m.text()).isEqualTo("Hello, how are you today?"));
    }
}
