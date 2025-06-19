package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_STREAMING_TOOLS_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_TOKENIZER_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_TOKENIZER_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.streamingChatResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.watsonx.WatsonxChatRequestParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageAssistant;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageSystem;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatMessageUser;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatParameterTool;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatMessage.TextChatToolCall;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatParameters.TextChatResponseFormat;
import io.quarkiverse.langchain4j.watsonx.bean.TextChatRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.quarkus.test.QuarkusUnitTest;

public class ChatAllPropertiesTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.space-id", "my-space-id")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "60s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.version", "aaaa-mm-dd")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.timeout", "60s")
            .overrideConfigKey("quarkus.langchain4j.watsonx.mode", "chat")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.frequency-penalty", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.logprobs", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.top-logprobs", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.model-id", "my_super_model")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.max-tokens", "200")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.n", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.presence-penalty", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.seed", "41")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.stop", "word1,word2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.temperature", "1.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.top-p", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.response-format", "json_object")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.tool-choice", "required")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.tool-choice-name", "myfunction")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.built-in-service.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.built-in-service.log-responses", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    static TextChatParameters parameters = TextChatParameters.builder()
            .frequencyPenalty(2.0)
            .logprobs(true)
            .topLogprobs(2)
            .maxTokens(200)
            .n(2)
            .presencePenalty(2.0)
            .seed(41)
            .stop(List.of("word1", "word2"))
            .temperature(1.5)
            .timeLimit(60000L)
            .topP(0.5)
            .responseFormat(new TextChatResponseFormat("json_object", null))
            .toolChoice("myfunction")
            .build();

    static List<TextChatParameterTool> tools = List
            .of(TextChatParameterTool.of(ToolSpecification.builder().name("myfunction").build()));

    @Test
    void check_config() throws Exception {
        var runtimeConfig = langchain4jWatsonConfig.defaultConfig();
        assertEquals(URL_WATSONX_SERVER, runtimeConfig.baseUrl().orElse(null).toString());
        assertEquals(URL_IAM_SERVER, runtimeConfig.iam().baseUrl().toString());
        assertEquals(API_KEY, runtimeConfig.apiKey().orElse(null));
        assertEquals("my-space-id", runtimeConfig.spaceId().orElse(null));
        assertEquals(PROJECT_ID, runtimeConfig.projectId().orElse(null));
        assertEquals(Duration.ofSeconds(60), runtimeConfig.timeout().get());
        assertEquals(Duration.ofSeconds(60), runtimeConfig.iam().timeout().get());
        assertEquals(true, runtimeConfig.logRequests().orElse(false));
        assertEquals(true, runtimeConfig.logResponses().orElse(false));
        assertEquals("aaaa-mm-dd", runtimeConfig.version());
        assertEquals("my_super_model", runtimeConfig.chatModel().modelName());
        assertEquals(2.0, runtimeConfig.chatModel().frequencyPenalty());
        assertEquals(true, runtimeConfig.chatModel().logprobs());
        assertEquals(2, runtimeConfig.chatModel().topLogprobs().orElse(null));
        assertEquals(200, runtimeConfig.chatModel().maxTokens());
        assertEquals(2, runtimeConfig.chatModel().n());
        assertEquals(2.0, runtimeConfig.chatModel().presencePenalty());
        assertEquals(41, runtimeConfig.chatModel().seed().orElse(null));
        assertEquals(List.of("word1", "word2"), runtimeConfig.chatModel().stop().orElse(null));
        assertEquals(1.5, runtimeConfig.chatModel().temperature());
        assertEquals(0.5, runtimeConfig.chatModel().topP());
        assertEquals("json_object", runtimeConfig.chatModel().responseFormat().orElse(null));
        assertEquals(ToolChoice.REQUIRED, runtimeConfig.chatModel().toolChoice().orElse(null));
        assertEquals("myfunction", runtimeConfig.chatModel().toolChoiceName().orElse(null));
        assertEquals(true, langchain4jWatsonConfig.builtInService().logRequests().orElse(false));
        assertEquals(true, langchain4jWatsonConfig.builtInService().logResponses().orElse(false));
    }

    @Test
    void chat_request_test() throws Exception {
        // Use the chat method without customization:
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.chatModel().modelName();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        var messages = List.<TextChatMessage> of(
                TextChatMessageSystem.of("You are an helpful assistant"),
                TextChatMessageUser.of("Hello, how are you?"));

        TextChatRequest body = new TextChatRequest(modelId, spaceId, projectId, messages, tools, parameters);
        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_CHAT_API)
                .build();

        List<ChatMessage> chatMessages = List.of(
                SystemMessage.from("You are an helpful assistant"),
                UserMessage.from("Hello, how are you?"));

        var response = chatModel.chat(
                ChatRequest.builder()
                        .messages(chatMessages)
                        .parameters(
                                ChatRequestParameters.builder()
                                        .toolSpecifications(ToolSpecification.builder().name("myfunction").build())
                                        .responseFormat(ResponseFormat.JSON)
                                        .build())
                        .build());

        ChatResponse expected = new ChatResponse.Builder()
                .aiMessage(AiMessage.from("AI Response"))
                .metadata(ChatResponseMetadata.builder()
                        .id("cmpl-15475d0dea9b4429a55843c77997f8a9")
                        .modelName("mistralai/mistral-large")
                        .tokenUsage(new TokenUsage(59, 47))
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();

        assertEquals(expected, response);
        // ----------------------------------------------

        // Use the chat method with customization:
        var request = ChatRequest.builder()
                .messages(chatMessages)
                .parameters(
                        WatsonxChatRequestParameters.builder()
                                .modelName("deepseek")
                                .frequencyPenalty(6.6)
                                .logprobs(false)
                                .topLogprobs(1)
                                .maxOutputTokens(100)
                                .n(10)
                                .presencePenalty(6.6)
                                .responseFormat(ResponseFormat.TEXT)
                                .seed(1)
                                .stopSequences("[]")
                                .temperature(0.0)
                                .toolChoice(ToolChoice.REQUIRED)
                                .toolChoiceName("mysupertool")
                                .toolSpecifications(ToolSpecification.builder().name("mysupertool").build())
                                .topP(1.0)
                                .build())
                .build();

        body = new TextChatRequest("deepseek", spaceId, projectId, messages,
                List.of(TextChatParameterTool.of(ToolSpecification.builder().name("mysupertool").build())),
                TextChatParameters.builder()
                        .frequencyPenalty(6.6)
                        .logprobs(false)
                        .topLogprobs(1)
                        .maxTokens(100)
                        .n(10)
                        .presencePenalty(6.6)
                        .responseFormat(null)
                        .seed(1)
                        .stop(List.of("[]"))
                        .temperature(0.0)
                        .toolChoiceOption(null)
                        .toolChoice("mysupertool")
                        .timeLimit(60000L)
                        .topP(1.0)
                        .build());

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_CHAT_API)
                .build();

        response = chatModel.chat(request);
        assertEquals(expected, response);
        // ----------------------------------------

        // Use the chat method with customization:
        request = ChatRequest.builder()
                .messages(chatMessages)
                .parameters(
                        WatsonxChatRequestParameters.builder()
                                .modelName("deepseek")
                                .frequencyPenalty(6.6)
                                .logprobs(false)
                                .topLogprobs(1)
                                .maxOutputTokens(100)
                                .n(10)
                                .presencePenalty(6.6)
                                .responseFormat(ResponseFormat.TEXT)
                                .seed(1)
                                .stopSequences("[]")
                                .temperature(0.0)
                                .toolChoice(ToolChoice.AUTO)
                                .toolChoiceName("mysupertool")
                                .toolSpecifications(ToolSpecification.builder().name("mysupertool").build())
                                .topP(1.0)
                                .build())
                .build();

        body = new TextChatRequest("deepseek", spaceId, projectId, messages,
                List.of(TextChatParameterTool.of(ToolSpecification.builder().name("mysupertool").build())),
                TextChatParameters.builder()
                        .frequencyPenalty(6.6)
                        .logprobs(false)
                        .topLogprobs(1)
                        .maxTokens(100)
                        .n(10)
                        .presencePenalty(6.6)
                        .responseFormat(null)
                        .seed(1)
                        .stop(List.of("[]"))
                        .temperature(0.0)
                        .toolChoiceOption("auto")
                        .toolChoice(null)
                        .timeLimit(60000L)
                        .topP(1.0)
                        .build());

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_CHAT_API)
                .build();

        response = chatModel.chat(request);
        assertEquals(expected, response);
        // ----------------------------------------

        // Use the chat method with unsupported parameter:
        assertThrows(UnsupportedFeatureException.class, () -> chatModel.chat(ChatRequest.builder()
                .messages(chatMessages)
                .parameters(WatsonxChatRequestParameters.builder().topK(1).build())
                .build()));
        // ----------------------------------------
    }

    @Test
    void chat_request_streaming_test() throws Exception {

        var toolExecutionRequest = ToolExecutionRequest.builder()
                .name("myfunction")
                .build();

        // Use the chat method without customization:
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.chatModel().modelName();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        var messagesToSend = List.<TextChatMessage> of(
                TextChatMessageSystem.of("You are an helpful assistant"),
                TextChatMessageUser.of("Hello, how are you?"),
                TextChatMessageAssistant.of(List.of(TextChatToolCall.of(toolExecutionRequest))));

        TextChatRequest body = new TextChatRequest(modelId, spaceId, projectId, messagesToSend, tools, parameters);

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(RESPONSE_WATSONX_CHAT_STREAMING_TOOLS_API)
                .build();

        List<ChatMessage> chatMessages = List.of(
                SystemMessage.from("You are an helpful assistant"),
                UserMessage.from("Hello, how are you?"),
                AiMessage.from(toolExecutionRequest));

        var streamingResponse = new AtomicReference<ChatResponse>();
        streamingChatModel.chat(
                ChatRequest.builder()
                        .messages(chatMessages)
                        .parameters(ChatRequestParameters.builder()
                                .toolSpecifications(ToolSpecification.builder().name("myfunction").build())
                                .responseFormat(ResponseFormat.JSON)
                                .build())
                        .build(),
                streamingChatResponseHandler(streamingResponse));

        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse.get().aiMessage().toolExecutionRequests()).isNotNull();
        // ----------------------------------------------

        toolExecutionRequest = ToolExecutionRequest.builder()
                .name("mysupertool")
                .build();

        // Use the chat method with customization:
        var request = ChatRequest.builder()
                .messages(chatMessages)
                .parameters(
                        WatsonxChatRequestParameters.builder()
                                .modelName("deepseek")
                                .frequencyPenalty(6.6)
                                .logprobs(false)
                                .topLogprobs(1)
                                .maxOutputTokens(100)
                                .n(10)
                                .presencePenalty(6.6)
                                .responseFormat(ResponseFormat.TEXT)
                                .seed(1)
                                .stopSequences("[]")
                                .temperature(0.0)
                                .toolChoice(ToolChoice.REQUIRED)
                                .toolChoiceName("mysupertool")
                                .toolSpecifications(ToolSpecification.builder().name("mysupertool").build())
                                .topP(1.0)
                                .build())
                .build();

        body = new TextChatRequest("deepseek", spaceId, projectId, messagesToSend,
                List.of(TextChatParameterTool.of(ToolSpecification.builder().name("mysupertool").build())),
                TextChatParameters.builder()
                        .frequencyPenalty(6.6)
                        .logprobs(false)
                        .topLogprobs(1)
                        .maxTokens(100)
                        .n(10)
                        .presencePenalty(6.6)
                        .responseFormat(null)
                        .seed(1)
                        .stop(List.of("[]"))
                        .temperature(0.0)
                        .toolChoiceOption(null)
                        .toolChoice("mysupertool")
                        .timeLimit(60000L)
                        .topP(1.0)
                        .build());

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(RESPONSE_WATSONX_CHAT_STREAMING_TOOLS_API)
                .build();

        var streamingResponse2 = new AtomicReference<ChatResponse>();
        streamingChatModel.chat(request,
                streamingChatResponseHandler(streamingResponse2));

        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse2.get().aiMessage().toolExecutionRequests()).isNotNull();
        // ----------------------------------------

        // Use the chat method with customization:
        request = ChatRequest.builder()
                .messages(chatMessages)
                .parameters(
                        WatsonxChatRequestParameters.builder()
                                .modelName("deepseek")
                                .frequencyPenalty(6.6)
                                .logprobs(false)
                                .topLogprobs(1)
                                .maxOutputTokens(100)
                                .n(10)
                                .presencePenalty(6.6)
                                .responseFormat(ResponseFormat.TEXT)
                                .seed(1)
                                .stopSequences("[]")
                                .temperature(0.0)
                                .toolChoice(ToolChoice.AUTO)
                                .toolChoiceName("mysupertool")
                                .toolSpecifications(ToolSpecification.builder().name("mysupertool").build())
                                .topP(1.0)
                                .build())
                .build();

        body = new TextChatRequest("deepseek", spaceId, projectId, messagesToSend,
                List.of(TextChatParameterTool.of(ToolSpecification.builder().name("mysupertool").build())),
                TextChatParameters.builder()
                        .frequencyPenalty(6.6)
                        .logprobs(false)
                        .topLogprobs(1)
                        .maxTokens(100)
                        .n(10)
                        .presencePenalty(6.6)
                        .responseFormat(null)
                        .seed(1)
                        .stop(List.of("[]"))
                        .temperature(0.0)
                        .toolChoiceOption("auto")
                        .toolChoice(null)
                        .timeLimit(60000L)
                        .topP(1.0)
                        .build());

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(RESPONSE_WATSONX_CHAT_STREAMING_TOOLS_API)
                .build();

        var streamingResponse3 = new AtomicReference<ChatResponse>();
        streamingChatModel.chat(request,
                streamingChatResponseHandler(streamingResponse3));

        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse3.get().aiMessage().toolExecutionRequests()).isNotNull();
        // ----------------------------------------

        // Use the chat method with unsupported parameter:
        assertThrows(UnsupportedFeatureException.class, () -> streamingChatModel.chat(ChatRequest.builder()
                .messages(chatMessages)
                .parameters(WatsonxChatRequestParameters.builder().topK(1).build())
                .build(),
                streamingChatResponseHandler(streamingResponse3)));
        // ----------------------------------------
    }

    @Test
    void check_chat_model_config() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.chatModel().modelName();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        var messages = List.<TextChatMessage> of(
                TextChatMessageSystem.of("SystemMessage"),
                TextChatMessageUser.of("UserMessage"));

        TextChatRequest body = new TextChatRequest(modelId, spaceId, projectId, messages, tools, parameters);
        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_CHAT_API)
                .build();

        var request = ChatRequest.builder()
                .messages(List.of(
                        dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                        dev.langchain4j.data.message.UserMessage.from("UserMessage")))
                .parameters(
                        ChatRequestParameters.builder()
                                .toolSpecifications(ToolSpecification.builder().name("myfunction").build())
                                .responseFormat(ResponseFormat.JSON)
                                .build());

        assertEquals("AI Response", chatModel.chat(request.build()).aiMessage().text());
    }

    @Test
    void check_token_count_estimator() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.chatModel().modelName();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        var body = new TokenizationRequest(modelId, "test", spaceId, projectId);

        mockWatsonxBuilder(URL_WATSONX_TOKENIZER_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_TOKENIZER_API.formatted(modelId))
                .build();
    }

    @Test
    void check_chat_streaming_model_config() throws Exception {

        var toolExecutionRequest = ToolExecutionRequest.builder()
                .name("myfunction")
                .build();

        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.chatModel().modelName();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        var messagesToSend = List.<TextChatMessage> of(
                TextChatMessageSystem.of("SystemMessage"),
                TextChatMessageUser.of("UserMessage"),
                TextChatMessageAssistant.of(List.of(TextChatToolCall.of(toolExecutionRequest))));

        TextChatRequest body = new TextChatRequest(modelId, spaceId, projectId, messagesToSend, tools, parameters);

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(RESPONSE_WATSONX_CHAT_STREAMING_TOOLS_API)
                .build();

        var messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"),
                AiMessage.from(toolExecutionRequest));

        var request = ChatRequest.builder()
                .messages(messages)
                .parameters(
                        ChatRequestParameters.builder()
                                .toolSpecifications(ToolSpecification.builder().name("myfunction").build())
                                .responseFormat(ResponseFormat.JSON)
                                .build())
                .build();

        var streamingResponse = new AtomicReference<ChatResponse>();
        streamingChatModel.chat(request, streamingChatResponseHandler(streamingResponse));

        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse.get().aiMessage().toolExecutionRequests()).isNotNull();
    }
}
