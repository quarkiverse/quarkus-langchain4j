package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.RESPONSE_WATSONX_CHAT_STREAMING_TOOLS_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_STREAMING_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.Image.Detail;
import com.ibm.watsonx.ai.chat.model.ImageContent;
import com.ibm.watsonx.ai.chat.model.TextChatRequest;
import com.ibm.watsonx.ai.chat.model.TextContent;
import com.ibm.watsonx.ai.chat.model.Tool;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import com.ibm.watsonx.ai.chat.model.ToolMessage;
import com.ibm.watsonx.ai.chat.model.schema.JsonSchema;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;

public class AiChatServiceTest extends WatsonxMockTestBase {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideConfigKey("quarkus.langchain4j.watsonx.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.watsonx.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.watsonx.timeout", "60s")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WatsonxTestUtil.class, Calculator.class));

    @Singleton
    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @SystemMessage("This is a systemMessage")
    interface AIService {
        @UserMessage("This is a userMessage {text}")
        String chat(String text);

        @UserMessage("This is a userMessage {text}")
        Multi<String> streaming(String text);
    }

    @Singleton
    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface ImageDescriptor {
        String chat(Image image, @UserMessage String text);
    }

    @Singleton
    @RegisterAiService(tools = Calculator.class)
    @SystemMessage("This is a systemMessage")
    interface AIServiceWithTool {
        String chat(@MemoryId String memoryId, @UserMessage String text);

        Multi<String> streaming(@MemoryId String memoryId, @UserMessage String text);
    }

    @Inject
    AIService aiService;

    @Inject
    ImageDescriptor imageDescriptor;

    @Inject
    AIServiceWithTool aiServiceWithTool;

    @Inject
    ChatMemoryStore memory;

    @Singleton
    static class Calculator {
        @dev.langchain4j.agent.tool.Tool("Execute the sum of two numbers")
        @Blocking
        public int sum(int first, int second) {
            return first + second;
        }
    }

    static List<Tool> tools = List.of(
            Tool.of("sum", "Execute the sum of two numbers",
                    JsonSchema.object()
                            .property("first", JsonSchema.integer())
                            .property("second", JsonSchema.integer())
                            .required("first", "second")
                            .build()));

    @Test
    void chat() throws Exception {

        var messages = List.<ChatMessage> of(
                com.ibm.watsonx.ai.chat.model.SystemMessage.of("This is a systemMessage"),
                com.ibm.watsonx.ai.chat.model.UserMessage.text("This is a userMessage Hello"));

        mock.chat().reply(ChatReply.text("AI Response").usage(59, 47));

        assertEquals("AI Response", aiService.chat("Hello"));

        CapturedRequest request = mock.chat().singleRequest();
        assertWatsonxRequest(request, URL_WATSONX_CHAT_API);
        assertJsonBody(request, generateChatRequest(messages, null));
    }

    @Test
    void chat_with_image() throws Exception {

        var messages = List.<ChatMessage> of(
                com.ibm.watsonx.ai.chat.model.UserMessage.of(
                        TextContent.of("Tell me more about this image"),
                        ImageContent.of("jpeg", "test", Detail.LOW)));

        mock.chat().reply(ChatReply.text("The image depicts a white cat with yellow eyes.").usage(6422, 123));

        Image image = Image.builder()
                .base64Data("test")
                .mimeType("jpeg")
                .build();

        assertEquals("The image depicts a white cat with yellow eyes.",
                imageDescriptor.chat(image, "Tell me more about this image"));

        CapturedRequest request = mock.chat().singleRequest();
        assertWatsonxRequest(request, URL_WATSONX_CHAT_API);
        assertJsonBody(request, generateChatRequest(messages, null));
    }

    @Test
    void chat_with_tool() throws Exception {

        var STARTED = List.<ChatMessage> of(
                com.ibm.watsonx.ai.chat.model.SystemMessage.of("This is a systemMessage"),
                com.ibm.watsonx.ai.chat.model.UserMessage.text("Execute the sum of 1 + 1"));

        mock.chat().replies(List.of(
                ChatReply.toolCall("sum", "{\"first\":1, \"second\":1}").usage(84, 25),
                ChatReply.text("The result is 2").usage(59, 47)));

        var TOOL_CALL = List.<ChatMessage> of(
                com.ibm.watsonx.ai.chat.model.SystemMessage.of("This is a systemMessage"),
                com.ibm.watsonx.ai.chat.model.UserMessage.text("Execute the sum of 1 + 1"),
                AssistantMessage.tools(
                        ToolCall.of("call_mock_0", "sum", "{\"first\":1, \"second\":1}")),
                ToolMessage.of("2", "call_mock_0"));

        var result = aiServiceWithTool.chat("no_streaming", "Execute the sum of 1 + 1");
        assertEquals("The result is 2", result);

        var messages = memory.getMessages("no_streaming");
        assertEquals("This is a systemMessage", ((dev.langchain4j.data.message.SystemMessage) messages.get(0)).text());
        assertEquals("Execute the sum of 1 + 1", ((dev.langchain4j.data.message.UserMessage) messages.get(1)).singleText());
        assertEquals("The result is 2", ((AiMessage) messages.get(4)).text());

        if (messages.get(2) instanceof AiMessage aiMessage) {
            assertTrue(aiMessage.hasToolExecutionRequests());
            assertEquals("{\"first\":1, \"second\":1}", aiMessage.toolExecutionRequests().get(0).arguments());
        } else {
            fail("The third message is not of type AiMessage");
        }

        if (messages.get(3) instanceof ToolExecutionResultMessage toolResultMessage) {
            assertEquals(2, Integer.parseInt(toolResultMessage.text()));
        } else {
            fail("The fourth message is not of type ToolExecutionResultMessage");
        }

        List<CapturedRequest> requests = mock.chat().requests();
        assertThat(requests).hasSize(2);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_CHAT_API);
        assertJsonBody(requests.get(0), generateChatRequest(STARTED, tools));
        assertWatsonxRequest(requests.get(1), URL_WATSONX_CHAT_API);
        assertJsonBody(requests.get(1), generateChatRequest(TOOL_CALL, tools));
    }

    @Test
    void streaming_chat() throws Exception {

        var messages = List.<ChatMessage> of(
                com.ibm.watsonx.ai.chat.model.SystemMessage.of("This is a systemMessage"),
                com.ibm.watsonx.ai.chat.model.UserMessage.text("This is a userMessage Hello"));

        mock.chat().reply(ChatReply.text("Hello").chunks(List.of("He", "llo")).usage(38, 3));

        var result = aiService.streaming("Hello").collect().asList().await().indefinitely();
        assertEquals(List.of("He", "llo"), result);

        CapturedRequest request = mock.chat().singleRequest();
        assertWatsonxRequest(request, URL_WATSONX_CHAT_STREAMING_API);
        assertJsonBody(request, generateChatRequest(messages, null));
    }

    @Test
    void streaming_chat_with_tool() throws Exception {

        var STARTED = List.<ChatMessage> of(
                com.ibm.watsonx.ai.chat.model.SystemMessage.of("This is a systemMessage"),
                com.ibm.watsonx.ai.chat.model.UserMessage.text("Execute the sum of 1 + 1"));

        // the tool call arguments arrive in several fragments, which the neutral stream does not produce
        mock.chat().replies(List.of(
                sse(RESPONSE_WATSONX_CHAT_STREAMING_TOOLS_API),
                ChatReply.text("The result is 2").chunks(List.of("The res", "ult is 2")).usage(88, 36)));

        var TOOL_CALL = List.<ChatMessage> of(
                com.ibm.watsonx.ai.chat.model.SystemMessage.of("This is a systemMessage"),
                com.ibm.watsonx.ai.chat.model.UserMessage.text("Execute the sum of 1 + 1"),
                AssistantMessage.tools(
                        ToolCall.of("chatcmpl-tool-7cf5dfd7c52441e59a7585243b22a86a", "sum", "{\"first\": 1, \"second\": 1}")),
                ToolMessage.of("2", "chatcmpl-tool-7cf5dfd7c52441e59a7585243b22a86a"));

        var result = aiServiceWithTool.streaming("streaming", "Execute the sum of 1 + 1")
                .collect().asList().await().indefinitely();
        assertEquals(List.of("The res", "ult is 2"), result);

        var messages = memory.getMessages("streaming");
        assertEquals("This is a systemMessage", ((dev.langchain4j.data.message.SystemMessage) messages.get(0)).text());
        assertEquals("Execute the sum of 1 + 1", ((dev.langchain4j.data.message.UserMessage) messages.get(1)).singleText());
        assertEquals("The result is 2", ((AiMessage) messages.get(4)).text());

        if (messages.get(2) instanceof AiMessage aiMessage) {
            assertTrue(aiMessage.hasToolExecutionRequests());
            assertEquals("{\"first\": 1, \"second\": 1}", aiMessage.toolExecutionRequests().get(0).arguments());
        } else {
            fail("The third message is not of type AiMessage");
        }

        if (messages.get(3) instanceof ToolExecutionResultMessage toolResultMessage) {
            assertEquals(2, Integer.parseInt(toolResultMessage.text()));
        } else {
            fail("The fourth message is not of type ToolExecutionResultMessage");
        }

        List<CapturedRequest> requests = mock.chat().requests();
        assertThat(requests).hasSize(2);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_CHAT_STREAMING_API);
        assertJsonBody(requests.get(0), generateChatRequest(STARTED, tools));
        assertWatsonxRequest(requests.get(1), URL_WATSONX_CHAT_STREAMING_API);
        assertJsonBody(requests.get(1), generateChatRequest(TOOL_CALL, tools));
    }

    private TextChatRequest generateChatRequest(List<ChatMessage> messages, List<Tool> tools) {
        LangChain4jWatsonxConfig.WatsonxConfig watsonConfig = langchain4jWatsonConfig.defaultConfig();
        ChatModelConfig chatModelConfig = watsonConfig.chatModel();
        String modelId = chatModelConfig.modelName();
        String spaceId = watsonConfig.spaceId().orElse(null);
        String projectId = watsonConfig.projectId().orElse(null);

        return TextChatRequest.builder()
                .modelId(modelId)
                .projectId(projectId)
                .spaceId(spaceId)
                .messages(messages)
                .tools(tools)
                .maxCompletionTokens(1024)
                .temperature(1.0)
                .timeLimit(60000l)
                .build();
    }
}
