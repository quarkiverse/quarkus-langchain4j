package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_DEPLOYMENT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_TIME_LIMIT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_DEPLOYMENT_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_DEPLOYMENT_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.streamingChatResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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

import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.SystemMessage;
import com.ibm.watsonx.ai.chat.model.TextChatRequest;
import com.ibm.watsonx.ai.chat.model.UserMessage;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.watsonx.WatsonxDeploymentChatModel;
import dev.langchain4j.model.watsonx.WatsonxDeploymentStreamingChatModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class DeploymentChatModelTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.deployment-chat-model.deployment-id",
                    DEFAULT_DEPLOYMENT_ID)
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

    @Test
    void check_config() {
        var watsonxConfig = langchain4jWatsonConfig.defaultConfig();
        assertEquals(DEFAULT_DEPLOYMENT_ID, watsonxConfig.deploymentChatModel().deploymentId().orElseThrow());
        assertThat(watsonxConfig.projectId()).isEmpty();
        assertThat(watsonxConfig.spaceId()).isEmpty();
        assertThat(watsonxConfig.gatewayChatModel().modelName()).isEmpty();
    }

    @Test
    void model_implementations() {
        assertInstanceOf(WatsonxDeploymentChatModel.class, ClientProxy.unwrap(chatModel));
        assertInstanceOf(WatsonxDeploymentStreamingChatModel.class, ClientProxy.unwrap(streamingChatModel));
    }

    @Test
    void chat() throws Exception {

        mockWatsonxBuilder(URL_WATSONX_DEPLOYMENT_CHAT_API.formatted(DEFAULT_DEPLOYMENT_ID), 200)
                .body(mapper.writeValueAsString(defaultRequest()))
                .response(RESPONSE_WATSONX_CHAT_API)
                .build();

        var chatResponse = chatModel.chat(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"));

        assertEquals("AI Response", chatResponse.aiMessage().text());
        assertEquals("mistralai/mistral-large", chatResponse.metadata().modelName());
        assertEquals(106, chatResponse.metadata().tokenUsage().totalTokenCount());
    }

    @Test
    void chat_streaming() throws Exception {

        mockWatsonxBuilder(URL_WATSONX_DEPLOYMENT_CHAT_STREAMING_API.formatted(DEFAULT_DEPLOYMENT_ID), 200)
                .body(mapper.writeValueAsString(defaultRequest()))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(RESPONSE_WATSONX_CHAT_STREAMING_API)
                .build();

        var messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"));

        var streamingResponse = new AtomicReference<ChatResponse>();
        streamingChatModel.chat(messages, streamingChatResponseHandler(streamingResponse));

        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        var metadata = streamingResponse.get().metadata();
        assertThat(streamingResponse.get().aiMessage().text()).isEqualTo("Hello");
        assertEquals("chatcmpl-5d8c131decbb6978cba5df10267aa3ff", metadata.id());
        assertEquals("meta-llama/llama-4-maverick-17b-128e-instruct-fp8", metadata.modelName());
        assertEquals(41, metadata.tokenUsage().totalTokenCount());
    }

    private TextChatRequest defaultRequest() {
        return TextChatRequest.builder()
                .messages(List.<ChatMessage> of(
                        SystemMessage.of("SystemMessage"),
                        UserMessage.text("UserMessage")))
                .maxCompletionTokens(1024)
                .temperature(1.0)
                .timeLimit(DEFAULT_TIME_LIMIT.toMillis())
                .build();
    }
}
