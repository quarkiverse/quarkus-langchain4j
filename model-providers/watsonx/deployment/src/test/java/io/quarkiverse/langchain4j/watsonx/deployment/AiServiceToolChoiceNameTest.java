package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.DEFAULT_TIME_LIMIT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.RESPONSE_WATSONX_CHAT_STREAMING_TOOLS_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_STREAMING_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.TextChatRequest;
import com.ibm.watsonx.ai.chat.model.ToolCall;
import com.ibm.watsonx.ai.chat.model.ToolMessage;
import com.ibm.watsonx.ai.chat.model.schema.JsonSchema;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;

public class AiServiceToolChoiceNameTest extends WatsonxMockTestBase {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.tool-choice-name", "sum")
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(WatsonxTestUtil.class, Calculator.class));

    static com.ibm.watsonx.ai.chat.model.Tool tool = com.ibm.watsonx.ai.chat.model.Tool.of(
            "sum",
            "Execute the sum of two numbers",
            JsonSchema.object()
                    .property("first", JsonSchema.integer())
                    .property("second", JsonSchema.integer())
                    .required("first", "second")
                    .build());

    @Singleton
    @RegisterAiService(tools = Calculator.class)
    @SystemMessage("This is a systemMessage")
    interface AIService {
        String chat(@MemoryId String memoryId, @UserMessage String text);

        Multi<String> chatMulti(@MemoryId String memoryId, @UserMessage String text);
    }

    @Singleton
    static class Calculator {
        @Tool("Execute the sum of two numbers")
        @Blocking
        public int sum(int first, int second) {
            return first + second;
        }
    }

    @Inject
    AIService aiService;

    @Test
    void chat() throws Exception {

        var toolExecutionRequest = ToolCall.of("call_mock_0", "sum", "{\"first\":1, \"second\":1}");

        var firstCallChatMessages = List.<ChatMessage> of(
                com.ibm.watsonx.ai.chat.model.SystemMessage.of("This is a systemMessage"),
                com.ibm.watsonx.ai.chat.model.UserMessage.text("Execute the sum of 1 + 1"));

        var firstCallParameters = generateChatRequest(firstCallChatMessages, List.of(tool), true);

        mock.chat().replies(List.of(
                ChatReply.toolCall("sum", "{\"first\":1, \"second\":1}").usage(84, 25),
                ChatReply.text("The result is 2").usage(59, 47)));

        var secondCallChatMessages = List.<ChatMessage> of(
                com.ibm.watsonx.ai.chat.model.SystemMessage.of("This is a systemMessage"),
                com.ibm.watsonx.ai.chat.model.UserMessage.text("Execute the sum of 1 + 1"),
                AssistantMessage.tools(toolExecutionRequest),
                ToolMessage.of("2", toolExecutionRequest.id()));

        var secondCallParameters = generateChatRequest(secondCallChatMessages, List.of(tool), false);

        var result = aiService.chat("myId", "Execute the sum of 1 + 1");
        assertEquals("The result is 2", result);

        List<CapturedRequest> requests = mock.chat().requests();
        assertThat(requests).hasSize(2);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_CHAT_API);
        assertJsonBody(requests.get(0), firstCallParameters);
        assertWatsonxRequest(requests.get(1), URL_WATSONX_CHAT_API);
        assertJsonBody(requests.get(1), secondCallParameters);
    }

    @Test
    void streaming_chat() throws Exception {

        var toolExecutionRequest = ToolCall.of("chatcmpl-tool-7cf5dfd7c52441e59a7585243b22a86a", "sum",
                "{\"first\": 1, \"second\": 1}");

        var firstCallChatMessages = List.<ChatMessage> of(
                com.ibm.watsonx.ai.chat.model.SystemMessage.of("This is a systemMessage"),
                com.ibm.watsonx.ai.chat.model.UserMessage.text("Execute the sum of 1 + 1"));

        var firstCallParameters = generateChatRequest(firstCallChatMessages, List.of(tool), true);

        // the tool call arguments arrive in several fragments, which the neutral stream does not produce
        mock.chat().replies(List.of(
                sse(RESPONSE_WATSONX_CHAT_STREAMING_TOOLS_API),
                ChatReply.text("The result is 2").chunks(List.of("The res", "ult is 2")).usage(88, 36)));

        var secondCallChatMessages = List.<ChatMessage> of(
                com.ibm.watsonx.ai.chat.model.SystemMessage.of("This is a systemMessage"),
                com.ibm.watsonx.ai.chat.model.UserMessage.text("Execute the sum of 1 + 1"),
                AssistantMessage.tools(toolExecutionRequest),
                ToolMessage.of("2", toolExecutionRequest.id()));

        var secondCallParameters = generateChatRequest(secondCallChatMessages, List.of(tool), false);

        var result = aiService.chatMulti("streaming", "Execute the sum of 1 + 1")
                .collect().asList().await().indefinitely();
        assertEquals(List.of("The res", "ult is 2"), result);

        List<CapturedRequest> requests = mock.chat().requests();
        assertThat(requests).hasSize(2);
        assertWatsonxRequest(requests.get(0), URL_WATSONX_CHAT_STREAMING_API);
        assertJsonBody(requests.get(0), firstCallParameters);
        assertWatsonxRequest(requests.get(1), URL_WATSONX_CHAT_STREAMING_API);
        assertJsonBody(requests.get(1), secondCallParameters);
    }

    private TextChatRequest generateChatRequest(List<ChatMessage> messages, List<com.ibm.watsonx.ai.chat.model.Tool> tools,
            boolean withToolChoice) {
        LangChain4jWatsonxConfig.WatsonxConfig watsonxConfig = langchain4jWatsonConfig.defaultConfig();
        ChatModelConfig chatModelConfig = watsonxConfig.chatModel();
        String modelId = chatModelConfig.modelName();
        String spaceId = watsonxConfig.spaceId().orElse(null);
        String projectId = watsonxConfig.projectId().orElse(null);

        TextChatRequest.Builder request = TextChatRequest.builder()
                .maxCompletionTokens(1024)
                .temperature(1.0)
                .timeLimit(DEFAULT_TIME_LIMIT.toMillis());

        if (withToolChoice)
            request.toolChoice(Map.of("type", "function", "function", Map.of("name", "sum")));
        else
            request.toolChoiceOption("auto");

        return request
                .modelId(modelId)
                .spaceId(spaceId)
                .projectId(projectId)
                .messages(messages)
                .tools(tools)
                .build();
    }
}
