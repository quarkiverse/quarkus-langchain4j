package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.runtime.config.ChatModelConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class AiChatServiceTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.chat-model.model-id", "mistralai/mistral-large")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WireMockUtil.class, Calculator.class));

    @Override
    void handlerBeforeEach() {
        mockServers.mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_EMBEDDING_API, 200)
                .response("""
                        {
                            "model_id": "%s",
                            "results": [
                              {
                                "embedding": [
                                  -0.006929283,
                                  -0.005336422,
                                  -0.024047505
                                ]
                              }
                            ],
                            "created_at": "2024-02-21T17:32:28Z",
                            "input_token_count": 10
                        }
                        """)
                .build();
    }

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
    @RegisterAiService(tools = Calculator.class)
    @SystemMessage("This is a systemMessage")
    interface AIServiceWithTool {
        String chat(@MemoryId String memoryId, @UserMessage String text);

        Multi<String> streaming(@MemoryId String memoryId, @UserMessage String text);
    }

    @Inject
    AIService aiService;

    @Inject
    AIServiceWithTool aiServiceWithTool;

    @Inject
    ChatMemoryStore memory;

    @Singleton
    static class Calculator {

        @Inject
        EmbeddingModel embeddingModel;

        @Tool("Execute the sum of two numbers")
        public int sum(int first, int second) {
            assertEquals(3, embeddingModel.embed("test").content().vectorAsList().size());
            return first + second;
        }
    }

    static String TOOL_CALL = "[TOOL_CALLS] [{\\\"id\\\":\\\"1\\\",\\\"name\\\":\\\"sum\\\",\\\"arguments\\\":{\\\"first\\\":1,\\\"second\\\":1}}]</s>";

    @Test
    void chat() throws Exception {

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .body(mapper.writeValueAsString(generateRequest()))
                .response(WireMockUtil.RESPONSE_WATSONX_CHAT_API)
                .build();

        assertEquals("AI Response", aiService.chat("Hello"));
    }

    @Test
    void chat_with_tool() throws Exception {

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .scenario(Scenario.STARTED, "TOOL_CALL")
                .response("""
                        {
                            "model_id": "mistralai/mistral-large",
                            "created_at": "2024-01-21T17:06:14.052Z",
                            "results": [
                                {
                                    "generated_text": "%s",
                                    "generated_token_count": 5,
                                    "input_token_count": 50,
                                    "stop_reason": "eos_token",
                                    "seed": 2123876088
                                }
                            ]
                        }""".formatted(TOOL_CALL))
                .build();

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_API, 200)
                .scenario("TOOL_CALL", "AI_RESPONSE")
                .response("""
                        {
                            "model_id": "mistralai/mistral-large",
                            "created_at": "2024-01-21T17:06:14.052Z",
                            "results": [
                                {
                                    "generated_text": "The result is 2",
                                    "generated_token_count": 5,
                                    "input_token_count": 50,
                                    "stop_reason": "eos_token",
                                    "seed": 2123876088
                                }
                            ]
                        }""")
                .build();

        var result = aiServiceWithTool.chat("no_streaming", "Execute the sum of 1 + 1");
        assertEquals("The result is 2", result);

        var messages = memory.getMessages("no_streaming");
        assertEquals(messages.get(0).text(), "This is a systemMessage");
        assertEquals(messages.get(1).text(), "Execute the sum of 1 + 1");
        assertEquals(messages.get(4).text(), "The result is 2");

        if (messages.get(2) instanceof AiMessage aiMessage) {
            assertTrue(aiMessage.hasToolExecutionRequests());
            assertEquals(aiMessage.toolExecutionRequests().get(0).arguments(), "{\"first\":1,\"second\":1}");
        } else {
            fail("The third message is not of type AiMessage");
        }

        if (messages.get(3) instanceof ToolExecutionResultMessage toolResultMessage) {
            assertEquals(2, Integer.parseInt(toolResultMessage.text()));
        } else {
            fail("The fourth message is not of type ToolExecutionResultMessage");
        }
    }

    @Test
    void streaming_chat() throws Exception {

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_STREAMING_API, 200)
                .body(mapper.writeValueAsString(generateRequest()))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(WireMockUtil.RESPONSE_WATSONX_STREAMING_API)
                .build();

        var result = aiService.streaming("Hello").collect().asList().await().indefinitely();
        assertEquals(List.of(". ", "I'", "m ", "a beginner"), result);
    }

    @Test
    void streaming_chat_with_tool() throws Exception {

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_STREAMING_API, 200)
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .scenario(Scenario.STARTED, "TOOL_CALL")
                .response(
                        """
                                id: 1
                                event: message
                                data: {}

                                id: 2
                                event: message
                                data: {"modelId":"mistralai/mistral-large","results":[{"generated_text":"","generated_token_count":0,"input_token_count":2,"stop_reason":"not_finished"}]}

                                id: 3
                                event: message
                                data: {"modelId":"mistralai/mistral-large","results":[{"generated_text":"%s","generated_token_count":0,"input_token_count":2,"stop_reason":"not_finished"}]}

                                id: 4
                                event: close"""
                                .formatted(TOOL_CALL))
                .build();

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_CHAT_STREAMING_API, 200)
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .scenario("TOOL_CALL", "AI_RESPONSE")
                .response(
                        """
                                id: 1
                                event: message
                                data: {}

                                id: 2
                                event: message
                                data: {"modelId":"mistralai/mistral-large","results":[{"generated_text":"","generated_token_count":0,"input_token_count":2,"stop_reason":"not_finished"}]}

                                id: 3
                                event: message
                                data: {"modelId":"mistralai/mistral-large","results":[{"generated_text":"The result is 2","generated_token_count":0,"input_token_count":2,"stop_reason":"not_finished"}]}

                                id: 4
                                event: close""")
                .build();

        var result = aiServiceWithTool.streaming("streaming", "Execute the sum of 1 + 1").collect().asList().await()
                .indefinitely();
        assertEquals("The result is 2", result.get(0));

        var messages = memory.getMessages("streaming");
        assertEquals(messages.get(0).text(), "This is a systemMessage");
        assertEquals(messages.get(1).text(), "Execute the sum of 1 + 1");
        assertEquals(messages.get(4).text(), "The result is 2");

        if (messages.get(2) instanceof AiMessage aiMessage) {
            assertTrue(aiMessage.hasToolExecutionRequests());
            assertEquals(aiMessage.toolExecutionRequests().get(0).arguments(), "{\"first\":1,\"second\":1}");
        } else {
            fail("The third message is not of type AiMessage");
        }

        if (messages.get(3) instanceof ToolExecutionResultMessage toolResultMessage) {
            assertEquals(2, Integer.parseInt(toolResultMessage.text()));
        } else {
            fail("The fourth message is not of type ToolExecutionResultMessage");
        }
    }

    private TextGenerationRequest generateRequest() {
        LangChain4jWatsonxConfig.WatsonConfig watsonConfig = langchain4jWatsonConfig.defaultConfig();
        ChatModelConfig chatModelConfig = watsonConfig.chatModel();
        String modelId = langchain4jWatsonFixedRuntimeConfig.defaultConfig().chatModel().modelId();
        String projectId = watsonConfig.projectId();
        String input = new StringBuilder()
                .append("<s>[INST] This is a systemMessage [/INST]</s>")
                .append("[INST] This is a userMessage Hello [/INST]")
                .toString();
        Parameters parameters = Parameters.builder()
                .decodingMethod(chatModelConfig.decodingMethod())
                .temperature(chatModelConfig.temperature())
                .minNewTokens(chatModelConfig.minNewTokens())
                .maxNewTokens(chatModelConfig.maxNewTokens())
                .build();

        return new TextGenerationRequest(modelId, projectId, input, parameters);
    }
}
