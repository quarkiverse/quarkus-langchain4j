package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.DEFAULT_DEPLOYMENT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.DEFAULT_TIME_LIMIT;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_DEPLOYMENT_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_DEPLOYMENT_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.streamingChatResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.chat.ChatModeration;
import com.ibm.watsonx.ai.chat.model.ChatMessage;
import com.ibm.watsonx.ai.chat.model.SystemMessage;
import com.ibm.watsonx.ai.chat.model.TextChatRequest;
import com.ibm.watsonx.ai.chat.model.UserMessage;

import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.watsonx.WatsonxChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxDeploymentChatModel;
import dev.langchain4j.model.watsonx.WatsonxDeploymentStreamingChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class DeploymentChatModelTest extends WatsonxMockTestBase {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            // a deployment needs no project: an empty value keeps the mock's placeholder out
            .overrideConfigKey("quarkus.langchain4j.watsonx.project-id", "")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.deployment-chat-model.deployment-id",
                    DEFAULT_DEPLOYMENT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WatsonxTestUtil.class));

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

        mock.chat().reply(ChatReply.text("AI Response").usage(59, 47).modelName("mistralai/mistral-large"));

        var chatResponse = chatModel.chat(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"));

        assertEquals("AI Response", chatResponse.aiMessage().text());
        assertEquals("mistralai/mistral-large", chatResponse.metadata().modelName());
        assertEquals(106, chatResponse.metadata().tokenUsage().totalTokenCount());

        CapturedRequest request = mock.chat().singleRequest();
        assertWatsonxRequest(request, URL_WATSONX_DEPLOYMENT_CHAT_API.formatted(DEFAULT_DEPLOYMENT_ID));
        assertJsonBody(request, defaultRequest());
    }

    @Test
    void chat_streaming() throws Exception {

        mock.chat().reply(ChatReply.text("Hello").usage(38, 3).id("chatcmpl-5d8c131decbb6978cba5df10267aa3ff")
                .modelName("meta-llama/llama-4-maverick-17b-128e-instruct-fp8"));

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

        CapturedRequest request = mock.chat().singleRequest();
        assertWatsonxRequest(request, URL_WATSONX_DEPLOYMENT_CHAT_STREAMING_API.formatted(DEFAULT_DEPLOYMENT_ID));
        assertJsonBody(request, defaultRequest());
    }

    @Test
    void chat_with_moderations_throws_unsupported_feature_exception() {

        var moderations = ChatModeration.builder()
                .hap(h -> h.input(0.8f))
                .build();

        var chatRequest = ChatRequest.builder()
                .messages(
                        dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                        dev.langchain4j.data.message.UserMessage.from("UserMessage"))
                .parameters(WatsonxChatRequestParameters.builder()
                        .moderations(moderations)
                        .build())
                .build();

        assertThrows(UnsupportedFeatureException.class, () -> chatModel.chat(chatRequest));
        assertThat(apiRequests()).isEmpty();
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
