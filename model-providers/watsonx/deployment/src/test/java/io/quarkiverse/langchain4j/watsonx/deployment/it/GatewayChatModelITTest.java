package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.core.Json;
import com.ibm.watsonx.ai.core.exception.WatsonxException;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxChatResponseMetadata;
import dev.langchain4j.model.watsonx.WatsonxGatewayChatRequestParameters;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GATEWAY_MODEL", matches = ".+")
public class GatewayChatModelITTest {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String MODEL = System.getenv("WATSONX_GATEWAY_MODEL");

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "30s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.gateway-chat-model.model-name", MODEL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"wrong-key\".api-key", "wrong-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"wrong-key\".gateway-chat-model.model-name", MODEL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"properties\".gateway-chat-model.model-name", MODEL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"properties\".gateway-chat-model.temperature", "1.0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"properties\".gateway-chat-model.max-output-tokens",
                    "1000")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"properties\".gateway-chat-model.store", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"properties\".gateway-chat-model.user", "my-user")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"properties\".gateway-chat-model.metadata.my-key",
                    "my-value")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    @ModelName("properties")
    ChatModel allPropertiesChatModel;

    @Inject
    @ModelName("wrong-key")
    ChatModel wrongKeyChatModel;

    @Test
    void test_chat() {

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .build();

        var chatResponse = assertDoesNotThrow(() -> chatModel.chat(request));
        var text = chatResponse.aiMessage().text();

        assertNotNull(chatResponse);
        assertNotNull(text);
        assertFalse(text.isBlank());

        assertNotNull(chatResponse.finishReason());
    }

    @Test
    void test_chat_response_metadata() {

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .build();

        var chatResponse = assertDoesNotThrow(() -> chatModel.chat(request));

        // The gateway returns the metadata of the provider that served the request.
        var metadata = assertInstanceOf(WatsonxChatResponseMetadata.class, chatResponse.metadata());
        assertNotNull(metadata.id());
        assertNotNull(metadata.modelName());
        assertNotNull(metadata.finishReason());
        assertNotNull(metadata.tokenUsage());
        assertTrue(metadata.tokenUsage().totalTokenCount() > 0);
    }

    @Test
    void test_chat_json() {

        record Poem(String content, String topic) {
        }

        var parameters = ChatRequestParameters.builder()
                .temperature(1.0)
                .responseFormat(ResponseFormat.JSON)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("""
                        Create a poem about dog, max 3 lines
                        Answer using the following json structure:
                        {
                            "content": <poem content>
                            "topic": <poem topic>
                        }"""))
                .parameters(parameters)
                .build();

        var chatResponse = assertDoesNotThrow(() -> chatModel.chat(request));
        var poem = Json.fromJson(chatResponse.aiMessage().text(), Poem.class);

        assertNotNull(chatResponse);
        assertNotNull(poem);
        assertFalse(poem.content().isBlank());
        assertTrue(poem.topic.equalsIgnoreCase("dog"));
    }

    @Test
    void test_chat_json_schema() {

        record Poem(String content, String topic) {
        }

        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("Animal")
                        .rootElement(JsonObjectSchema.builder()
                                .addStringProperty("content")
                                .addEnumProperty("topic", List.of("dog", "cat"))
                                .required("content", "topic")
                                .additionalProperties(true)
                                .build())

                        .build())
                .build();

        var parameters = ChatRequestParameters.builder()
                .responseFormat(responseFormat)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Create a poem about dog, max 3 lines"))
                .parameters(parameters)
                .build();

        var chatResponse = assertDoesNotThrow(() -> chatModel.chat(request));
        var poem = Json.fromJson(chatResponse.aiMessage().text(), Poem.class);

        assertNotNull(chatResponse);
        assertNotNull(poem);
        assertFalse(poem.content().isBlank());
        assertTrue(poem.topic.equalsIgnoreCase("dog"));
    }

    @Test
    void test_chat_tool_without_params() {

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("What time is it?"))
                .toolSpecifications(ToolSpecification.builder()
                        .name("get_time")
                        .description("Get the current time")
                        .build())
                .build();

        var chatResponse = assertDoesNotThrow(() -> chatModel.chat(request));
        assertNotNull(chatResponse);

        var tools = chatResponse.aiMessage().toolExecutionRequests();
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertNotNull(tools.get(0).id());
        assertEquals("get_time", tools.get(0).name());
        assertEquals("{}", tools.get(0).arguments());
    }

    @Test
    void test_chat_tool_choice_option() {

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .toolChoice(ToolChoice.REQUIRED)
                .toolSpecifications(
                        ToolSpecification.builder()
                                .name("send_email")
                                .description("Send an email")
                                .parameters(JsonObjectSchema.builder()
                                        .addStringProperty("to")
                                        .addStringProperty("subject")
                                        .addStringProperty("body")
                                        .required("to", "body")
                                        .additionalProperties(true)
                                        .build())
                                .build())
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello!"))
                .parameters(parameters)
                .build();

        var chatResponse = assertDoesNotThrow(() -> chatModel.chat(request));
        var assistantMessage = chatResponse.aiMessage();
        assertTrue(assistantMessage.text() == null || assistantMessage.text().isBlank());
        assertNotNull(assistantMessage.toolExecutionRequests());
        assertEquals(1, assistantMessage.toolExecutionRequests().size());
    }

    @Test
    void test_chat_with_gateway_parameters() {

        var parameters = WatsonxGatewayChatRequestParameters.builder()
                .temperature(1.0)
                .maxOutputTokens(5)
                // The metadata is accepted by the provider only when the output is stored.
                .store(true)
                .user("my-user")
                .metadata(Map.of("my-key", "my-value"))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a long story about a dog."))
                .parameters(parameters)
                .build();

        var chatResponse = assertDoesNotThrow(() -> chatModel.chat(request));

        assertNotNull(chatResponse.aiMessage().text());
        assertNotNull(chatResponse.modelName());
        assertTrue(chatResponse.tokenUsage().outputTokenCount() <= 5);
    }

    @Test
    void test_chat_with_properties() {

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a long story about a dog."))
                .build();

        var chatResponse = assertDoesNotThrow(() -> allPropertiesChatModel.chat(request));

        assertNotNull(chatResponse.aiMessage().text());
        assertTrue(chatResponse.tokenUsage().outputTokenCount() <= 1000);
    }

    @Test
    void test_chat_streaming() {

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatResponse> chatResponse = new AtomicReference<>();
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        StringBuilder partialResponses = new StringBuilder();

        streamingChatModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partialResponses.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                chatResponse.set(completeResponse);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                throwable.set(error);
                latch.countDown();
            }
        });

        assertDoesNotThrow(() -> latch.await(30, TimeUnit.SECONDS));
        assertNull(throwable.get());

        var text = chatResponse.get().aiMessage().text();
        assertNotNull(text);
        assertFalse(text.isBlank());
        assertEquals(text, partialResponses.toString());

        assertNotNull(chatResponse.get().finishReason());
        assertNotNull(chatResponse.get().tokenUsage());
    }

    @Test
    void test_chat_with_invalid_api_key() {

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello!"))
                .build();

        var ex = assertThrows(LangChain4jException.class, () -> wrongKeyChatModel.chat(request));
        assertTrue(ex.getMessage().contains("Provided API key could not be found."));
    }

    @Test
    void test_chat_with_model_not_configured_in_the_gateway() {

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello!"))
                .parameters(ChatRequestParameters.builder()
                        .modelName("model-that-does-not-exist")
                        .build())
                .build();

        var ex = assertThrows(LangChain4jException.class, () -> chatModel.chat(request));
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        assertNotNull(watsonxEx.details().orElseThrow().errors());
    }
}
