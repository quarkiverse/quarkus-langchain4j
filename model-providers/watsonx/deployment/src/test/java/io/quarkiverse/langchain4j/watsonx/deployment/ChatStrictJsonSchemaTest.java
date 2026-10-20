package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.DEFAULT_TIME_LIMIT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.streamingChatResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.chat.model.BaseChatParameters.JsonSchemaObject;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.ChatParameters;
import com.ibm.watsonx.ai.chat.model.SystemMessage;
import com.ibm.watsonx.ai.chat.model.TextChatRequest;
import com.ibm.watsonx.ai.chat.model.UserMessage;

import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkus.test.QuarkusExtensionTest;

public class ChatStrictJsonSchemaTest extends WatsonxMockTestBase {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.response-format", "json_schema")
            // "strict-json-schema" is deliberately not set: this test verifies that the strict mode is the default.
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WatsonxTestUtil.class));

    static JsonSchema jsonSchema = JsonSchema.builder()
            .name("test")
            .rootElement(
                    JsonObjectSchema.builder()
                            .addProperty("name", JsonStringSchema.builder().build())
                            .build())
            .build();

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void test_chat_strict_json_schema_input() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.chatModel().modelName();
        String projectId = config.projectId().orElse(null);

        var messages = List.<ChatMessage> of(
                SystemMessage.of("SystemMessage"),
                UserMessage.text("UserMessage"));

        var body = TextChatRequest.builder()
                .modelId(modelId)
                .projectId(projectId)
                .messages(messages)
                .timeLimit(DEFAULT_TIME_LIMIT.toMillis())
                .maxCompletionTokens(1024)
                .temperature(1.0)
                .responseFormat(ChatParameters.ResponseFormat.JSON_SCHEMA.value())
                .jsonSchema(new JsonSchemaObject("test",
                        JsonSchemaElementUtils.toMap(jsonSchema.rootElement(), true), true))
                .build();

        mock.chat().replies(List.of(ChatReply.text("AI Response").usage(59, 47), ChatReply.text("Hello").usage(38, 3)));

        var chatMessages = List.<dev.langchain4j.data.message.ChatMessage> of(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"));

        var chatRequest = ChatRequest.builder()
                .messages(chatMessages)
                .responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON).jsonSchema(jsonSchema).build())
                .build();

        var response = chatModel.chat(chatRequest);

        assertEquals("AI Response", response.aiMessage().text());

        var streamingResponse = new AtomicReference<ChatResponse>();
        streamingChatModel.chat(chatRequest, streamingChatResponseHandler(streamingResponse));

        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse.get().aiMessage().text())
                .isNotNull()
                .isEqualTo("Hello");

        List<CapturedRequest> requests = mock.chat().requests();
        assertThat(requests).hasSize(2);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_CHAT_API);
        assertJsonBody(requests.get(0), body);
        assertWatsonxRequest(requests.get(1), URL_WATSONX_CHAT_STREAMING_API);
        assertJsonBody(requests.get(1), body);
    }
}
