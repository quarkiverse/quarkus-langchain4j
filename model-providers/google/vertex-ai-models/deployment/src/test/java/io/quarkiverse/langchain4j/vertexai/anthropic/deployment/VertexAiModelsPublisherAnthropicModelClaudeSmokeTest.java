package io.quarkiverse.langchain4j.vertexai.anthropic.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkiverse.langchain4j.vertexai.runtime.models.VertexAiChatModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiModelsPublisherAnthropicModelClaudeSmokeTest extends WiremockAware {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "claude-opus-4-6";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.chat-model.location", "dummy")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.chat-model.project-id", "dummy");

    @Inject
    ChatModel chatLanguageModel;

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(chatLanguageModel)).isInstanceOf(VertexAiChatModel.class);

        wiremock().register(
                post(urlEqualTo(
                        String.format("/v1/projects/dummy/locations/dummy/publishers/anthropic/models/%s:rawPredict",
                                CHAT_MODEL_ID)))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        """
                                                {
                                                   "model": "claude-opus-4-6",
                                                   "id": "msg_vrtx_014TKqWf17gBdBijMw8pBmGF",
                                                   "type": "message",
                                                   "role": "assistant",
                                                   "content": [
                                                     {
                                                       "type": "text",
                                                       "text": "Hi there! How are you doing today? Is there something I can help you with? 😊"
                                                     }
                                                   ],
                                                   "stop_reason": "end_turn",
                                                   "stop_sequence": null,
                                                   "usage": {
                                                     "input_tokens": 9,
                                                     "cache_creation_input_tokens": 0,
                                                     "cache_read_input_tokens": 0,
                                                     "cache_creation": {
                                                       "ephemeral_5m_input_tokens": 0,
                                                       "ephemeral_1h_input_tokens": 0
                                                     },
                                                     "output_tokens": 24
                                                   }
                                                }
                                                """)));

        UserMessage userMessage = UserMessage.from("Hello");

        ChatResponse response = chatLanguageModel.chat(ChatRequest.builder().messages(List.of(userMessage)).build());
        assertThat(response.aiMessage().text()).containsAnyOf("Hi there!", "How are you doing today?");

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("Quarkus REST Client");
        String requestBody = new String(loggedRequest.getBody());
        assertThat(requestBody).contains("Hello");
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {
        @Override
        public String getAuthorization(Input input) {
            return "Bearer " + API_KEY;
        }

    }

}
