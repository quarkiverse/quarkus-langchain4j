package io.quarkiverse.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.chat.model.ChatParameters.ResponseFormat;
import com.ibm.watsonx.ai.chat.model.ThinkingEffort;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkus.test.QuarkusUnitTest;

public class ChatAllPropertiesTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.space-id", "my-space-id")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", "my-project-id")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "120s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.version", "2015-02-13")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.timeout", "120s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.frequency-penalty", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.logprobs", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.top-logprobs", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.model-name", "my_super_model")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.max-output-tokens", "200")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.presence-penalty", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.seed", "41")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.stop", "word1,word2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.temperature", "1.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.top-p", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.response-format", "json")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.tool-choice", "required")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.tool-choice-name", "my_function")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.log-requests-curl", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.guided-choice", "1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.guided-grammar", "guidedGrammar")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.guided-regex", "guidedRegex")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.repetition-penalty", "1.1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.length-penalty", "1.2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.thinking.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.thinking.tags.think", "think")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.thinking.tags.response", "response")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.thinking.effort", "low")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.thinking.include-reasoning", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    static final String EXPECTED_BODY = """
            {
                "model_id": "my_super_model",
                "project_id": "my-project-id",
                "space_id": "my-space-id",
                "messages": [
                    {
                        "role": "system",
                        "content": "You are a helpful assistant."
                    },
                    {
                        "role": "user",
                        "content": [{
                                "type": "text",
                                "text": "Who won the world series in 2020?"
                        }]
                    }
                ],
                "tools": [{
                    "type": "function",
                    "function": {
                        "name": "my_function"
                    }
                }],
                "frequency_penalty": 2.0,
                "logprobs": true,
                "top_logprobs": 2,
                "max_completion_tokens": 200,
                "presence_penalty": 2.0,
                "seed": 41,
                "stop": ["word1", "word2"],
                "temperature": 1.5,
                "top_p": 0.5,
                "response_format": {
                    "type": "json_object"
                },
                "tool_choice": {"type": "function", "function": {"name": "my_function"}},
                "time_limit": 120000,
                "chat_template_kwargs": {
                    "thinking": true
                },
                "include_reasoning": true,
                "reasoning_effort": "low",
                "repetition_penalty": 1.1,
                "length_penalty": 1.2,
                "guided_choice": ["1"],
                "guided_grammar": "guidedGrammar",
                "guided_regex": "guidedRegex"
            }""";

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType().orElse(null))
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Test
    void check_config() throws Exception {
        var runtimeConfig = langchain4jWatsonConfig.defaultConfig();
        var thinking = runtimeConfig.chatModel().thinking().orElse(null);
        assertEquals(URL_WATSONX_SERVER, runtimeConfig.baseUrl().orElse(null).toString());
        assertEquals(URL_IAM_SERVER, runtimeConfig.iam().baseUrl().orElse(null).toString());
        assertEquals(API_KEY, runtimeConfig.apiKey().orElse(null));
        assertEquals("my-space-id", runtimeConfig.spaceId().orElse(null));
        assertEquals("my-project-id", runtimeConfig.projectId().orElse(null));
        assertEquals(Duration.ofSeconds(120), runtimeConfig.timeout().orElse(null));
        assertEquals(Duration.ofSeconds(120), runtimeConfig.iam().timeout().get());
        assertEquals(true, runtimeConfig.chatModel().logRequests().orElse(false));
        assertEquals(true, runtimeConfig.chatModel().logResponses().orElse(false));
        assertEquals(true, runtimeConfig.chatModel().logRequestsCurl().orElse(false));
        assertEquals("2015-02-13", runtimeConfig.version().orElse(null));
        assertEquals("my_super_model", runtimeConfig.chatModel().modelName());
        assertEquals(2.0, runtimeConfig.chatModel().frequencyPenalty());
        assertEquals(true, runtimeConfig.chatModel().logprobs());
        assertEquals(2, runtimeConfig.chatModel().topLogprobs().orElse(null));
        assertEquals(200, runtimeConfig.chatModel().maxOutputTokens());
        assertEquals(2.0, runtimeConfig.chatModel().presencePenalty());
        assertEquals(41, runtimeConfig.chatModel().seed().orElse(null));
        assertEquals(List.of("word1", "word2"), runtimeConfig.chatModel().stop().orElse(null));
        assertEquals(1.5, runtimeConfig.chatModel().temperature());
        assertEquals(0.5, runtimeConfig.chatModel().topP());
        assertEquals(ResponseFormat.JSON, runtimeConfig.chatModel().responseFormat().orElse(null));
        assertEquals(ToolChoice.REQUIRED, runtimeConfig.chatModel().toolChoice().orElse(null));
        assertEquals("my_function", runtimeConfig.chatModel().toolChoiceName().orElse(null));
        assertNotNull(thinking);
        assertEquals(ThinkingEffort.LOW, thinking.effort().orElse(null));
        assertTrue(thinking.enabled().orElse(false));
        assertTrue(thinking.includeReasoning().orElse(false));
        assertNotNull(thinking.tags().orElse(null));
        assertEquals("think", thinking.tags().get().think());
        assertEquals("response", thinking.tags().get().response().orElse(null));
        assertEquals(1.1, runtimeConfig.chatModel().repetitionPenalty().orElse(null));
        assertEquals(1.2, runtimeConfig.chatModel().lengthPenalty().orElse(null));
        assertEquals(new TreeSet<>(Set.of("1")), runtimeConfig.chatModel().guidedChoice().orElse(null));
        assertEquals("guidedGrammar", runtimeConfig.chatModel().guidedGrammar().orElse(null));
        assertEquals("guidedRegex", runtimeConfig.chatModel().guidedRegex().orElse(null));
    }

    @Test
    void check_chat_model_parameters() {

        mockWatsonxBuilder(URL_WATSONX_CHAT_API.formatted("2015-02-13"), 200)
                .responseMediaType("application/json")
                .body(equalToJson(EXPECTED_BODY))
                .response(RESPONSE_WATSONX_CHAT_API)
                .build();

        var messages = List.of(
                SystemMessage.from("You are a helpful assistant."),
                UserMessage.from("Who won the world series in 2020?"));

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(
                        ToolSpecification.builder()
                                .name("my_function")
                                .build())
                .build();

        var result = chatModel.chat(chatRequest);
        assertNotNull(result);
    }

    @Test
    void check_streaming_chat_model_parameters() {

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API.formatted("2015-02-13"), 200)
                .responseMediaType("application/json")
                .body(equalToJson(EXPECTED_BODY))
                .response(RESPONSE_WATSONX_CHAT_STREAMING_API)
                .build();

        var messages = List.of(
                SystemMessage.from("You are a helpful assistant."),
                UserMessage.from("Who won the world series in 2020?"));

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(
                        ToolSpecification.builder()
                                .name("my_function")
                                .build())
                .build();

        CountDownLatch waitForResult = new CountDownLatch(1);
        streamingChatModel.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                waitForResult.countDown();
            }

            @Override
            public void onError(Throwable error) {
            }

        });

        assertDoesNotThrow(() -> waitForResult.await(3, TimeUnit.SECONDS));
    }
}
