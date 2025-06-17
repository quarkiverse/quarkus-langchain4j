package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_TIME_LIMIT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_STREAMING_TOOLS_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageAssistant;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageSystem;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageTool;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageUser;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatParameterTool;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatToolCall;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatRequest;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;

public class AiServiceToolChoiceNameTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.tool-choice-name", "sum")
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(WireMockUtil.class, Calculator.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    static ToolSpecification tool = ToolSpecification.builder()
            .description("Execute the sum of two numbers")
            .name("sum")
            .parameters(
                    JsonObjectSchema.builder()
                            .addIntegerProperty("first")
                            .addIntegerProperty("second")
                            .required("first", "second")
                            .build())
            .build();

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

        var toolExecutionRequest = ToolExecutionRequest.builder()
                .name("sum")
                .id("chatcmpl-tool-3f621ce6ad9240da963d661215621711")
                .arguments("{\"first\":1, \"second\":1}")
                .build();

        var firstCallChatMessages = List.<TextChatMessage> of(
                TextChatMessageSystem.of("This is a systemMessage"),
                TextChatMessageUser.of("Execute the sum of 1 + 1"));

        var firstCallParameters = generateChatRequest(firstCallChatMessages, List.of(TextChatParameterTool.of(tool)), true);

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(firstCallParameters))
                .scenario(Scenario.STARTED, "TOOL_CALL")
                .response("""
                            {
                                "id": "chat-2e8d342d8ced41d89c0ff4efd32b3f9d",
                                "model_id": "mistralai/mistral-large",
                                "choices": [{
                                    "index": 0,
                                    "message": {
                                        "role": "assistant",
                                        "tool_calls": [
                                            {
                                                "id": "chatcmpl-tool-3f621ce6ad9240da963d661215621711",
                                                "type": "function",
                                                "function": {
                                                    "name": "sum",
                                                    "arguments": "{\\\"first\\\":1, \\\"second\\\":1}"
                                                }
                                            }
                                        ]
                                    },
                                    "finish_reason": "tool_calls"
                                }],
                                "created": 1728808696,
                                "model_version": "2.0.0",
                                "created_at": "2024-10-13T08:38:16.960Z",
                                "usage": {
                                    "completion_tokens": 25,
                                    "prompt_tokens": 84,
                                    "total_tokens": 109
                                }
                        }""")
                .build();

        var toolExecutionResultMessage = ToolExecutionResultMessage.from(toolExecutionRequest, "2");

        var secondCallChatMessages = List.<TextChatMessage> of(
                TextChatMessageSystem.of("This is a systemMessage"),
                TextChatMessageUser.of("Execute the sum of 1 + 1"),
                TextChatMessageAssistant.of(List.of(TextChatToolCall.of(toolExecutionRequest))),
                TextChatMessageTool.of(toolExecutionResultMessage));

        var secondCallParameters = generateChatRequest(secondCallChatMessages, List.of(TextChatParameterTool.of(tool)), false);

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(secondCallParameters))
                .scenario("TOOL_CALL", "AI_RESPONSE")
                .response("""
                        {
                            "id": "cmpl-15475d0dea9b4429a55843c77997f8a9",
                            "model_id": "mistralai/mistral-large",
                            "created": 1728806666,
                            "created_at": "2024-10-13T08:04:26.200Z",
                            "choices": [{
                                "index": 0,
                                "message": {
                                    "role": "assistant",
                                    "content": "The result is 2"
                                },
                                "finish_reason": "stop"
                            }],
                            "usage": {
                                "completion_tokens": 47,
                                "prompt_tokens": 59,
                                "total_tokens": 106
                            }
                        }""")
                .build();

        var result = aiService.chat("myId", "Execute the sum of 1 + 1");
        assertEquals("The result is 2", result);
    }

    @Test
    void streaming_chat() throws Exception {

        var toolExecutionRequest = ToolExecutionRequest.builder()
                .name("sum")
                .id("chatcmpl-tool-7cf5dfd7c52441e59a7585243b22a86a")
                .arguments("{\"first\": 1, \"second\": 1}")
                .build();

        var firstCallChatMessages = List.<TextChatMessage> of(
                TextChatMessageSystem.of("This is a systemMessage"),
                TextChatMessageUser.of("Execute the sum of 1 + 1"));

        var firstCallParameters = generateChatRequest(firstCallChatMessages, List.of(TextChatParameterTool.of(tool)), true);

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 200)
                .body(mapper.writeValueAsString(firstCallParameters))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .scenario(Scenario.STARTED, "TOOL_CALL")
                .response(RESPONSE_WATSONX_CHAT_STREAMING_TOOLS_API)
                .build();

        var toolExecutionResultMessage = ToolExecutionResultMessage.from(toolExecutionRequest, "2");

        var secondCallChatMessages = List.<TextChatMessage> of(
                TextChatMessageSystem.of("This is a systemMessage"),
                TextChatMessageUser.of("Execute the sum of 1 + 1"),
                TextChatMessageAssistant.of(List.of(TextChatToolCall.of(toolExecutionRequest))),
                TextChatMessageTool.of(toolExecutionResultMessage));

        var secondCallParameters = generateChatRequest(secondCallChatMessages, List.of(TextChatParameterTool.of(tool)), false);

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 200)
                .body(mapper.writeValueAsString(secondCallParameters))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .scenario("TOOL_CALL", "AI_RESPONSE")
                .response(
                        """
                                id: 1
                                event: message
                                data: {"id":"chat-049e3ff7ff08416fb5c334d05af059da","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"role":"assistant"}}],"created":1728810714,"model_version":"2.0.0","created_at":"2024-10-13T09:11:55.072Z","usage":{"prompt_tokens":88,"total_tokens":88},"system":{"warnings":[{"message":"This model is a Non-IBM Product governed by a third-party license that may impose use restrictions and other obligations. By using this model you agree to its terms as identified in the following URL.","id":"disclaimer_warning","more_info":"https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx"},{"message":"The value of 'time_limit' for this model must be larger than 0 and not larger than 10m0s; it was set to 10m0s","id":"time_limit_out_of_range","additional_properties":{"limit":600000,"new_value":600000,"parameter":"time_limit","value":999000}}]}}

                                id: 2
                                event: message
                                data: {"id":"chat-049e3ff7ff08416fb5c334d05af059da","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":null,"delta":{"content":"The res"}}],"created":1728810714,"model_version":"2.0.0","created_at":"2024-10-13T09:11:55.073Z","usage":{"completion_tokens":1,"prompt_tokens":88,"total_tokens":89}}

                                id: 3
                                event: message
                                data: {"id":"chat-049e3ff7ff08416fb5c334d05af059da","model_id":"mistralai/mistral-large","choices":[{"index":0,"finish_reason":"stop","delta":{"content":"ult is 2"}}],"created":1728810714,"model_version":"2.0.0","created_at":"2024-10-13T09:11:55.090Z","usage":{"completion_tokens":2,"prompt_tokens":88,"total_tokens":90}}

                                id: 4
                                event: message
                                data: {"id":"chat-049e3ff7ff08416fb5c334d05af059da","model_id":"mistralai/mistral-large","choices":[],"created":1728810714,"model_version":"2.0.0","created_at":"2024-10-13T09:11:55.715Z","usage":{"completion_tokens":36,"prompt_tokens":88,"total_tokens":124}}

                                id: 5
                                event: close
                                data: {}
                                """)
                .build();

        var result = aiService.chatMulti("streaming", "Execute the sum of 1 + 1")
                .collect().asList().await().indefinitely();
        assertEquals(List.of("The res", "ult is 2"), result);
    }

    private TextChatRequest generateChatRequest(List<TextChatMessage> messages, List<TextChatParameterTool> tools,
            boolean withToolChoice) {
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = langchain4jWatsonConfig.defaultConfig();
        ChatModelConfig chatModelConfig = watsonConfig.chatModel();
        String modelId = chatModelConfig.modelName();
        String spaceId = watsonConfig.spaceId().orElse(null);
        String projectId = watsonConfig.projectId().orElse(null);

        TextChatParameters.Builder builder = TextChatParameters.builder()
                .frequencyPenalty(0.0)
                .logprobs(false)
                .maxTokens(1024)
                .n(1)
                .presencePenalty(0.0)
                .temperature(1.0)
                .topP(1.0)
                .stop(List.of())
                .timeLimit(DEFAULT_TIME_LIMIT);

        if (withToolChoice)
            builder.toolChoice("sum");
        else
            builder.toolChoiceOption("auto");

        return new TextChatRequest(modelId, spaceId, projectId, messages, tools, builder.build());
    }
}
