package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_TIME_LIMIT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.streamingChatResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageSystem;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageUser;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatParameters.TextChatResponseFormat;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatParameters.TextChatResponseFormat.JsonSchemaObject;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatRequest;
import io.quarkus.test.QuarkusUnitTest;

public class ChatJsonSchemaTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.response-format", "json_schema")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .response("my_super_token", new Date())
                .build();
    }

    static JsonSchema jsonSchema = JsonSchema.builder()
            .name("test")
            .rootElement(
                    JsonObjectSchema.builder()
                            .addProperty("name", JsonStringSchema.builder().build())
                            .build())
            .build();

    static TextChatParameters parameters = TextChatParameters.builder()
            .frequencyPenalty(0.0)
            .logprobs(false)
            .maxTokens(1024)
            .n(1)
            .presencePenalty(0.0)
            .temperature(1.0)
            .topP(1.0)
            .stop(List.of())
            .timeLimit(DEFAULT_TIME_LIMIT)
            .responseFormat(new TextChatResponseFormat("json_schema",
                    new JsonSchemaObject("test", JsonSchemaElementUtils.toMap(jsonSchema.rootElement()),
                            true)))
            .build();

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void test_capabilities() {
        assertEquals(1, chatModel.supportedCapabilities().size());
        assertEquals(Capability.RESPONSE_FORMAT_JSON_SCHEMA, chatModel.supportedCapabilities().iterator().next());
        assertEquals(1, streamingChatModel.supportedCapabilities().size());
        assertEquals(Capability.RESPONSE_FORMAT_JSON_SCHEMA,
                streamingChatModel.supportedCapabilities().iterator().next());
    }

    @Test
    void test_chat_json_schema_input() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.chatModel().modelName();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        var messages = List.<TextChatMessage> of(
                TextChatMessageSystem.of("SystemMessage"),
                TextChatMessageUser.of("UserMessage"));

        TextChatRequest body = new TextChatRequest(modelId, spaceId, projectId, messages, null, parameters);

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_CHAT_API)
                .build();

        var chatMessages = List.<ChatMessage> of(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"));

        var chatRequest = ChatRequest.builder()
                .messages(chatMessages)
                .responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON).jsonSchema(jsonSchema).build())
                .build();

        var response = chatModel.chat(chatRequest);

        assertEquals("AI Response", response.aiMessage().text());

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 200)
                .body(mapper.writeValueAsString(body))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(RESPONSE_WATSONX_CHAT_STREAMING_API)
                .build();

        var streamingResponse = new AtomicReference<ChatResponse>();
        streamingChatModel.chat(chatRequest, streamingChatResponseHandler(streamingResponse));

        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse.get().aiMessage().text())
                .isNotNull()
                .isEqualTo(" Hello");
    }
}
