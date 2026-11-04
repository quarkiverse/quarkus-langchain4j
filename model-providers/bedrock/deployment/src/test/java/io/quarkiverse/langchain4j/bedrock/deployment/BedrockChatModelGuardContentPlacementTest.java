package io.quarkiverse.langchain4j.bedrock.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.createContentBlockDelta;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.createContentBlockStop;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.createMessageStart;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.createMessageStop;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.createMetadata;
import static io.quarkiverse.langchain4j.bedrock.deployment.BedrockStreamHelper.decode;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusExtensionTest;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;

class BedrockChatModelGuardContentPlacementTest extends BedrockTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<ChatMessage> MESSAGES = List.of(
            UserMessage.from("First user message"),
            AiMessage.from("Assistant message"),
            UserMessage.from("Last user message"));

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(BedrockStreamHelper.class)
                    .addClass(TestCredentialsProvider.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.model-id", "amazon.titan-text-express-v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.guardrail.guardrail-identifier", "guardrail-id")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.guardrail.guard-content-placement",
                    "last-user-message")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.chat-model.aws.endpoint-override",
                    "http://localhost:%d".formatted(WM_PORT))
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
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.all-user-messages.chat-model.aws.endpoint-override",
                    "http://localhost:%d".formatted(WM_PORT))
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.all-user-messages.chat-model.aws.credentials-provider",
                    "TestCredentialsProvider")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.bedrock.without-placement.chat-model.model-id", "amazon.titan-text-express-v1")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.bedrock.without-placement.chat-model.guardrail.guardrail-identifier", "guardrail-id")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.without-placement.chat-model.aws.region", "eu-central-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.bedrock.without-placement.chat-model.aws.endpoint-override",
                    "http://localhost:%d".formatted(WM_PORT))
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

    @Test
    void should_send_guard_content_for_the_last_user_message() throws Exception {
        // given
        stubChatResponse();

        // when
        chatModel.chat(MESSAGES);

        // then
        assertGuardContentPlacement(requestBody(), false, true);
    }

    @Test
    void should_send_guard_content_for_all_user_messages() throws Exception {
        // given
        stubChatResponse();

        // when
        allUserMessagesChatModel.chat(MESSAGES);

        // then
        assertGuardContentPlacement(requestBody(), true, true);
    }

    @Test
    void should_not_send_guard_content_when_placement_is_not_configured() throws Exception {
        // given
        stubChatResponse();

        // when
        defaultPlacementChatModel.chat(MESSAGES);

        // then
        assertGuardContentPlacement(requestBody(), false, false);
    }

    @Test
    void should_send_guard_content_for_the_last_user_message_when_streaming() throws Throwable {
        // given
        var helper = BedrockStreamHelper.create();
        stubStreamingChatResponse();

        // when
        streamingChatModel.chat(MESSAGES, helper);
        helper.awaitResponse();

        // then
        assertGuardContentPlacement(requestBody(), false, true);
    }

    private static void stubChatResponse() {
        stubFor(post(anyUrl())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "output": {
                                    "message": {
                                      "role": "assistant",
                                      "content": [{ "text": "response" }]
                                    }
                                  },
                                  "stopReason": "end_turn",
                                  "usage": { "inputTokens": 10, "outputTokens": 1, "totalTokens": 11 },
                                  "metrics": { "latencyMs": 1 }
                                }
                                """)));
    }

    private static void stubStreamingChatResponse() {
        var events = List.of(
                createMessageStart(ConversationRole.ASSISTANT),
                createContentBlockDelta(0, "response"),
                createContentBlockStop(0),
                createMessageStop(StopReason.END_TURN),
                createMetadata(1, 10, 1));
        stubFor(post(anyUrl())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/vnd.amazon.eventstream")
                        .withHeader("Transfer-Encoding", "chunked")
                        .withHeader("Connection", "keep-alive")
                        .withBody(decode(events))));
    }

    private static JsonNode requestBody() throws Exception {
        var serveEvents = wireMockServer.getAllServeEvents();
        assertThat(serveEvents).hasSize(1);
        return OBJECT_MAPPER.readTree(serveEvents.get(0).getRequest().getBodyAsString());
    }

    private static void assertGuardContentPlacement(JsonNode requestBody, boolean guardAllUserMessages,
            boolean guardLastUserMessage) {
        var messages = requestBody.path("messages");

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).at("/content/0/guardContent/text/text").isMissingNode())
                .isEqualTo(!guardAllUserMessages);
        assertThat(messages.get(0).at("/content/0/guardContent/text/text").asText())
                .isEqualTo(guardAllUserMessages ? "First user message" : "");
        assertThat(messages.get(1).at("/content/0/guardContent").isMissingNode()).isTrue();
        assertThat(messages.get(2).at("/content/0/guardContent/text/text").isMissingNode())
                .isEqualTo(!guardLastUserMessage);
        assertThat(messages.get(2).at("/content/0/guardContent/text/text").asText())
                .isEqualTo(guardLastUserMessage ? "Last user message" : "");
    }
}
