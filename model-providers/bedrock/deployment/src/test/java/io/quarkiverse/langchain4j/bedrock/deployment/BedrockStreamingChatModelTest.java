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

import dev.langchain4j.model.bedrock.BedrockStreamingChatModel;
import dev.langchain4j.model.bedrock.BedrockTokenUsage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BedrockStreamingChatModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestCredentialsProvider.class)
                    .addClass(BedrockStreamHelper.class)
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
    StreamingChatModel streamingChatModel;

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
        assertThat(ClientProxy.unwrap(streamingChatModel)).isInstanceOf(BedrockStreamingChatModel.class);
    }

    @Test
    void should_answer_a_chat_message() throws Throwable {
        // given
        var helper = BedrockStreamHelper.create();
        mock.chat().reply(ChatReply.text("Hello, how are you today?").usage(10, 17));

        // when
        streamingChatModel.chat("Hello, how are you today?", helper);
        var response = helper.awaitResponse();

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isEqualTo("Hello, how are you today?");
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.metadata().modelName()).isEqualTo("amazon.titan-text-express-v1");
        assertThat(response.metadata().tokenUsage()).isEqualTo(BedrockTokenUsage.builder()
                .inputTokenCount(10)
                .outputTokenCount(17)
                .build());
        assertThat(listener.onRequestCount()).isEqualTo(1);
        assertThat(listener.onResponseCount()).isEqualTo(1);
        assertThat(listener.onErrorCount()).isEqualTo(0);
        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/bedrock/model/amazon.titan-text-express-v1/converse-stream");
        assertThat(request.asChat().messages()).singleElement()
                .satisfies(m -> assertThat(m.text()).isEqualTo("Hello, how are you today?"));
    }
}
