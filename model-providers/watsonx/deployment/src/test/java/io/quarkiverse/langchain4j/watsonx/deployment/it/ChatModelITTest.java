package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.core.Json;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.watsonx.WatsonxChatRequestParameters;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class ChatModelITTest {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "30s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"wrong-key\".api-key", "wrong-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"think-model\".chat-model.model-name",
                    "ibm/granite-3-3-8b-instruct")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"think-model\".chat-model.thinking.tags.think", "think")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"think-model\".chat-model.thinking.tags.response",
                    "response")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    ChatModel chatModel;

    @Inject
    @ModelName("wrong-key")
    ChatModel wrongKeyChatModel;

    @Inject
    @ModelName("think-model")
    ChatModel thinkingChatModel;

    @Test
    void test_chat() {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .build();
        var chatResponse = assertDoesNotThrow(() -> chatModel.chat(chatRequest));
        var text = chatResponse.aiMessage().text();

        assertNotNull(chatResponse);
        assertNotNull(text);
        assertFalse(text.isBlank());

        assertNotNull(chatResponse.finishReason());
    }

    @Test
    void test_chat_json() {

        record Poem(String content, String topic) {
        }

        var parameters = ChatRequestParameters.builder()
                .temperature(0.0)
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
                                .build())
                        .build())
                .build();

        var parameters = ChatRequestParameters.builder()
                .temperature(0.0)
                .modelName("mistralai/mistral-small-3-1-24b-instruct-2503")
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
    void test_chat_thinking() {

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Why the sky is blue?"))
                .build();

        var chatResponse = assertDoesNotThrow(() -> thinkingChatModel.chat(request));
        var text = chatResponse.aiMessage().text();

        assertNotNull(chatResponse);
        assertNotNull(text);
        assertFalse(text.isBlank());
        assertFalse(text.contains("<think>") && text.contains("</think>"));
        assertFalse(text.contains("<response>") && text.contains("</response>"));

        var thinkingMessage = chatResponse.aiMessage().thinking();
        assertNotNull(thinkingMessage);
        assertFalse(thinkingMessage.isBlank());
    }

    @Test
    void test_chat_thinking_with_parameters() {

        WatsonxChatRequestParameters parameters = WatsonxChatRequestParameters.builder()
                .modelName("ibm/granite-3-3-8b-instruct")
                .thinking(new ExtractionTags("think", "response"))
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Why the sky is blue?"))
                .parameters(parameters)
                .build();

        var chatResponse = assertDoesNotThrow(() -> chatModel.chat(request));
        var text = chatResponse.aiMessage().text();

        assertNotNull(chatResponse);
        assertNotNull(text);
        assertFalse(text.isBlank());
        assertFalse(text.contains("<think>") && text.contains("</think>"));
        assertFalse(text.contains("<response>") && text.contains("</response>"));

        var thinkingMessage = chatResponse.aiMessage().thinking();
        assertNotNull(thinkingMessage);
        assertFalse(thinkingMessage.isBlank());
    }

    @Test
    void test_chat_tool_without_params() {

        ChatRequest request = ChatRequest.builder()
                .modelName("mistralai/mistral-small-3-1-24b-instruct-2503")
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
    void test_chat_with_invalid_api_key() {

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello!"))
                .build();

        var ex = assertThrows(LangChain4jException.class, () -> wrongKeyChatModel.chat(request));
        assertTrue(ex.getMessage().contains("Provided API key could not be found."));
    }
}
