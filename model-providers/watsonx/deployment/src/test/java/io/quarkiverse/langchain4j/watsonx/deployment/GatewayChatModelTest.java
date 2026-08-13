package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_GATEWAY_CHAT_MODEL;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_TIME_LIMIT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_GATEWAY_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_GATEWAY_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_GATEWAY_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.streamingChatResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.SystemMessage;
import com.ibm.watsonx.ai.chat.model.UserMessage;
import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ReasoningEffort;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.ServiceTier;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayParameters.StreamOptions;
import com.ibm.watsonx.ai.gateway.chat.ModelGatewayTextChatRequest;

import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.watsonx.WatsonxChatResponseMetadata;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusUnitTest;

public class GatewayChatModelTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.gateway-chat-model.model-name",
                    DEFAULT_GATEWAY_CHAT_MODEL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.model-name",
                    DEFAULT_GATEWAY_CHAT_MODEL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.service-tier",
                    "priority")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.reasoning-effort",
                    "low")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.cache.enabled",
                    "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.cache.threshold",
                    "0.9")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.modalities",
                    "text")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.store", "false")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.parallel-tool-calls", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.user", "my-user")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.metadata.my-key", "my-value")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.temperature", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.max-output-tokens",
                    "10")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.frequency-penalty",
                    "0.2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.presence-penalty",
                    "0.3")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.top-p", "0.8")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-chat-model.logprobs", "true")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .response("my_super_token", new Date())
                .build();
    }

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    @ModelName("all-properties")
    ChatModel allPropertiesChatModel;

    @Test
    void check_config() {
        var gatewayConfig = langchain4jWatsonConfig.defaultConfig().gatewayChatModel();
        assertEquals(DEFAULT_GATEWAY_CHAT_MODEL, gatewayConfig.modelName().orElseThrow());
        assertThat(gatewayConfig.serviceTier()).isEmpty();
        assertThat(gatewayConfig.reasoningEffort()).isEmpty();
        assertThat(gatewayConfig.cache()).isEmpty();
        assertThat(gatewayConfig.metadata()).isEmpty();
        assertThat(gatewayConfig.frequencyPenalty()).isEmpty();
        assertThat(gatewayConfig.presencePenalty()).isEmpty();
        assertThat(gatewayConfig.topP()).isEmpty();
        assertThat(gatewayConfig.logprobs()).isEmpty();

        var allProperties = langchain4jWatsonConfig.namedConfig().get("all-properties").gatewayChatModel();
        assertEquals(ServiceTier.PRIORITY, allProperties.serviceTier().orElseThrow());
        assertEquals(ReasoningEffort.LOW, allProperties.reasoningEffort().orElseThrow());
        assertEquals(true, allProperties.cache().orElseThrow().enabled().orElseThrow());
        assertEquals(0.9, allProperties.cache().orElseThrow().threshold().orElseThrow());
        assertEquals(List.of("text"), allProperties.modalities().orElseThrow());
        assertEquals(false, allProperties.store().orElseThrow());
        assertEquals(true, allProperties.parallelToolCalls().orElseThrow());
        assertEquals("my-user", allProperties.user().orElseThrow());
        assertEquals(Map.of("my-key", "my-value"), allProperties.metadata());
        assertEquals(0.2, allProperties.frequencyPenalty().orElseThrow());
        assertEquals(0.3, allProperties.presencePenalty().orElseThrow());
        assertEquals(0.8, allProperties.topP().orElseThrow());
        assertEquals(true, allProperties.logprobs().orElseThrow());
    }

    @Test
    void chat() throws Exception {

        mockWatsonxBuilder(URL_WATSONX_GATEWAY_CHAT_API, 200)
                .body(mapper.writeValueAsString(defaultRequest().build()))
                .response(RESPONSE_WATSONX_GATEWAY_CHAT_API)
                .build();

        var chatResponse = chatModel.chat(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"));

        assertEquals("AI Response", chatResponse.aiMessage().text());
        assertNotNull(chatResponse.metadata().id());
        assertEquals(FinishReason.STOP, chatResponse.metadata().finishReason());
        assertEquals(106, chatResponse.metadata().tokenUsage().totalTokenCount());

        // Gateway specific metadata.
        var metadata = assertInstanceOf(WatsonxChatResponseMetadata.class, chatResponse.metadata());
        assertEquals("default", metadata.getServiceTier());
        assertEquals("fp_44709d6fcb", metadata.getSystemFingerprint());
        assertEquals(false, metadata.getCached());
    }

    @Test
    void chat_with_all_properties() throws Exception {

        var body = ModelGatewayTextChatRequest.builder()
                .model(DEFAULT_GATEWAY_CHAT_MODEL)
                .messages(List.<ChatMessage> of(UserMessage.text("UserMessage")))
                .frequencyPenalty(0.2)
                .logprobs(true)
                .maxCompletionTokens(10)
                .presencePenalty(0.3)
                .temperature(0.5)
                .topP(0.8)
                .timeLimit(DEFAULT_TIME_LIMIT.toMillis())
                .serviceTier("priority")
                .reasoningEffort("low")
                .router(new ModelGatewayParameters.Router(
                        new ModelGatewayParameters.Cache(true, null, 0.9)))
                .modalities(List.of("text"))
                .store(false)
                .parallelToolCalls(true)
                .user("my-user")
                .metadata(Map.of("my-key", "my-value"))
                .build();

        mockWatsonxBuilder(URL_WATSONX_GATEWAY_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_GATEWAY_CHAT_API)
                .build();

        assertEquals("AI Response",
                allPropertiesChatModel.chat(dev.langchain4j.data.message.UserMessage.from("UserMessage"))
                        .aiMessage().text());
    }

    @Test
    void chat_streaming() throws Exception {

        var body = defaultRequest()
                .stream(true)
                .streamOptions(new StreamOptions(true))
                .build();

        mockWatsonxBuilder(URL_WATSONX_GATEWAY_CHAT_API, 200)
                .body(mapper.writeValueAsString(body))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(RESPONSE_WATSONX_GATEWAY_CHAT_STREAMING_API)
                .build();

        var messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"));

        var streamingResponse = new AtomicReference<ChatResponse>();
        streamingChatModel.chat(messages, streamingChatResponseHandler(streamingResponse));

        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse.get().aiMessage().text()).isEqualTo("Hello");
        assertNotNull(streamingResponse.get().metadata().id());
        assertEquals(FinishReason.STOP, streamingResponse.get().metadata().finishReason());
        assertEquals(41, streamingResponse.get().metadata().tokenUsage().totalTokenCount());
    }

    @Test
    void chat_error() throws Exception {

        mockWatsonxBuilder(URL_WATSONX_GATEWAY_CHAT_API, 404)
                .responseMediaType(MediaType.APPLICATION_JSON)
                .response("""
                        {
                            "errors": [
                                {
                                    "code": "model_not_supported",
                                    "message": "Model 'openai/gpt-4o-mini' is not supported"
                                }
                            ],
                            "trace": "3ba1905572c91c5e3e00b6f0d3f2f81f",
                            "status_code": 404
                        }""")
                .build();

        var ex = assertThrows(LangChain4jException.class, () -> chatModel.chat("UserMessage"));
        var watsonxEx = assertInstanceOf(WatsonxException.class, ex.getCause());
        var details = watsonxEx.details().orElseThrow();
        assertEquals(404, details.statusCode());
        assertEquals("model_not_supported", details.errors().get(0).code());
    }

    private ModelGatewayTextChatRequest.Builder defaultRequest() {
        return ModelGatewayTextChatRequest.builder()
                .model(DEFAULT_GATEWAY_CHAT_MODEL)
                .messages(List.<ChatMessage> of(
                        SystemMessage.of("SystemMessage"),
                        UserMessage.text("UserMessage")))
                .maxCompletionTokens(1024)
                .temperature(1.0)
                .timeLimit(DEFAULT_TIME_LIMIT.toMillis());
    }
}
