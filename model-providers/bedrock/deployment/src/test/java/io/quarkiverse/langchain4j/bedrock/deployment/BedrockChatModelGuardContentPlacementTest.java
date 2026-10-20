package io.quarkiverse.langchain4j.bedrock.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.databind.JsonNode;

class BedrockChatModelGuardContentPlacementTest {

    private static final List<ChatMessage> MESSAGES = List.of(
            UserMessage.from("First user message"),
            AiMessage.from("Assistant message"),
            UserMessage.from("Last user message"));

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(BedrockStreamHelper.class)
                    .addClass(TestCredentialsProvider.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.model-id", "amazon.titan-text-express-v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.guardrail.guardrail-identifier", "guardrail-id")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.guardrail.guard-content-placement",
                    "last-user-message")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.bedrock.all-user-messages.chat-model.model-id", "amazon.titan-text-express-v1")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.bedrock.all-user-messages.chat-model.guardrail.guardrail-identifier", "guardrail-id")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.bedrock.all-user-messages.chat-model.guardrail.guard-content-placement",
                    "all-user-messages")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.all-user-messages.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.all-user-messages.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.bedrock.without-placement.chat-model.model-id", "amazon.titan-text-express-v1")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.bedrock.without-placement.chat-model.guardrail.guardrail-identifier", "guardrail-id")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.without-placement.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.without-placement.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    @ModelName("all-user-messages")
    ChatModel allUserMessagesChatModel;

    @Inject
    @ModelName("without-placement")
    ChatModel defaultPlacementChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void should_send_guard_content_for_the_last_user_message() {
        // given
        mock.chat().reply(ChatReply.text("response").usage(10, 1));

        // when
        chatModel.chat(MESSAGES);

        // then
        assertGuardContentPlacement(requestBody(), false, true);
    }

    @Test
    void should_send_guard_content_for_all_user_messages() {
        // given
        mock.chat().reply(ChatReply.text("response").usage(10, 1));

        // when
        allUserMessagesChatModel.chat(MESSAGES);

        // then
        assertGuardContentPlacement(requestBody(), true, true);
    }

    @Test
    void should_not_send_guard_content_when_placement_is_not_configured() {
        // given
        mock.chat().reply(ChatReply.text("response").usage(10, 1));

        // when
        defaultPlacementChatModel.chat(MESSAGES);

        // then
        assertGuardContentPlacement(requestBody(), false, false);
    }

    @Test
    void should_send_guard_content_for_the_last_user_message_when_streaming() throws Throwable {
        // given
        var helper = BedrockStreamHelper.create();
        mock.chat().reply(ChatReply.text("response").usage(10, 1));

        // when
        streamingChatModel.chat(MESSAGES, helper);
        helper.awaitResponse();

        // then
        assertGuardContentPlacement(requestBody(), false, true);
    }

    private JsonNode requestBody() {
        return mock.chat().singleRequest().bodyAsJson();
    }

    private static void assertGuardContentPlacement(JsonNode requestBody, boolean guardAllUserMessages,
            boolean guardLastUserMessage) {
        var messages = requestBody.path("messages");
        assertThat(messages.size()).isEqualTo(3);
        assertThat(messages.get(0).at("/content/0/guardContent/text/text").isMissingNode())
                .isEqualTo(!guardAllUserMessages);
        assertThat(messages.get(0).at("/content/0/guardContent/text/text").asString())
                .isEqualTo(guardAllUserMessages ? "First user message" : "");
        assertThat(messages.get(1).at("/content/0/guardContent").isMissingNode()).isTrue();
        assertThat(messages.get(2).at("/content/0/guardContent/text/text").isMissingNode())
                .isEqualTo(!guardLastUserMessage);
        assertThat(messages.get(2).at("/content/0/guardContent/text/text").asString())
                .isEqualTo(guardLastUserMessage ? "Last user message" : "");
    }
}
