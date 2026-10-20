package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiGeminiParallelToolCallsTest extends WiremockAware {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "gemini-2.5-flash";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.base-url", WiremockAware.wiremockUrlForConfig());

    @Inject
    ChatModel chatModel;

    @BeforeEach
    void stubGenerateContent() {
        resetRequests();
        wiremock().register(
                post(urlPathEqualTo(String.format(
                        "/v1/projects/dummy/locations/dummy/publishers/google/models/%s:generateContent", CHAT_MODEL_ID)))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        """
                                                {"candidates":[{"content":{"role":"model","parts":[{"text":"done"}]},"finishReason":"STOP"}],
                                                 "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}
                                                """)));
    }

    @Test
    void parallelCallsAndTheirResultsAreSentAsOneContentEach() throws IOException {
        ToolExecutionRequest weather = request("getWeather", "{\"city\":\"Paris\"}", "signature-1");
        ToolExecutionRequest time = request("getTime", "{\"city\":\"Paris\"}", null);

        chatModel.chat(
                UserMessage.from("What is the weather and time in Paris?"),
                AiMessage.from(weather, time),
                ToolExecutionResultMessage.from(weather, "sunny"),
                ToolExecutionResultMessage.from(time, "12:00"),
                AiMessage.from("Sunny, and it is noon."),
                UserMessage.from("Thanks"));

        JsonNode contents = requestContents();
        assertThat(contents).hasSize(5);
        assertThat(roles(contents)).containsExactly("user", "model", "user", "model", "user");

        JsonNode calls = contents.get(1).get("parts");
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).at("/functionCall/name").asText()).isEqualTo("getWeather");
        assertThat(calls.get(0).get("thoughtSignature").asText()).isEqualTo("signature-1");
        assertThat(calls.get(1).at("/functionCall/name").asText()).isEqualTo("getTime");
        assertThat(calls.get(1).has("thoughtSignature")).isFalse();

        JsonNode results = contents.get(2).get("parts");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).at("/functionResponse/name").asText()).isEqualTo("getWeather");
        assertThat(results.get(0).at("/functionResponse/response/content").asText()).isEqualTo("sunny");
        assertThat(results.get(1).at("/functionResponse/name").asText()).isEqualTo("getTime");
        assertThat(results.get(1).at("/functionResponse/response/content").asText()).isEqualTo("12:00");

        assertThat(contents.get(3).at("/parts/0/text").asText()).isEqualTo("Sunny, and it is noon.");
        assertThat(contents.get(4).at("/parts/0/text").asText()).isEqualTo("Thanks");
    }

    @Test
    void repeatedCallsToTheSameToolAreAllKept() throws IOException {
        ToolExecutionRequest paris = request("getWeather", "{\"city\":\"Paris\"}", "signature-1");
        ToolExecutionRequest london = request("getWeather", "{\"city\":\"London\"}", null);

        chatModel.chat(
                UserMessage.from("What is the weather in Paris and London?"),
                AiMessage.from(paris, london),
                ToolExecutionResultMessage.from(paris, "sunny"),
                ToolExecutionResultMessage.from(london, "rainy"));

        JsonNode contents = requestContents();
        assertThat(roles(contents)).containsExactly("user", "model", "user");

        JsonNode calls = contents.get(1).get("parts");
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).at("/functionCall/args/city").asText()).isEqualTo("Paris");
        assertThat(calls.get(1).at("/functionCall/args/city").asText()).isEqualTo("London");

        JsonNode results = contents.get(2).get("parts");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).at("/functionResponse/response/content").asText()).isEqualTo("sunny");
        assertThat(results.get(1).at("/functionResponse/response/content").asText()).isEqualTo("rainy");
    }

    @Test
    void consecutiveToolTurnsStayInConversationOrder() throws IOException {
        ToolExecutionRequest search = request("search", "{\"query\":\"Paris\"}", "signature-1");
        ToolExecutionRequest weather = request("getWeather", "{\"city\":\"Paris\"}", "signature-2");
        ToolExecutionRequest time = request("getTime", "{\"city\":\"Paris\"}", null);

        chatModel.chat(
                UserMessage.from("Tell me about Paris"),
                AiMessage.from(search),
                ToolExecutionResultMessage.from(search, "capital of France"),
                AiMessage.from(weather, time),
                ToolExecutionResultMessage.from(weather, "sunny"),
                SystemMessage.from("Answer briefly."),
                ToolExecutionResultMessage.from(time, "12:00"));

        JsonNode contents = requestContents();
        assertThat(roles(contents)).containsExactly("user", "model", "user", "model", "user");
        assertThat(contents.at("/1/parts/0/functionCall/name").asText()).isEqualTo("search");
        assertThat(contents.at("/2/parts/0/functionResponse/name").asText()).isEqualTo("search");
        assertThat(contents.get(3).get("parts")).hasSize(2);
        assertThat(contents.get(4).get("parts")).hasSize(2);
    }

    private static ToolExecutionRequest request(String name, String arguments, String id) {
        return ToolExecutionRequest.builder().name(name).arguments(arguments).id(id).build();
    }

    private JsonNode requestContents() throws IOException {
        return new ObjectMapper().readTree(requestBodyOfSingleRequest()).get("contents");
    }

    private static List<String> roles(JsonNode contents) {
        return StreamSupport.stream(contents.spliterator(), false)
                .map(content -> content.get("role").asText())
                .toList();
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer " + API_KEY;
        }
    }
}
