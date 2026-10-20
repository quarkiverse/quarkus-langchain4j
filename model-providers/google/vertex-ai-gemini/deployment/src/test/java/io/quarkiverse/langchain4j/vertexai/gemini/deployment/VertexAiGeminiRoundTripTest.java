package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.internal.MockVertexAiGeminiAuthProvider;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiRoundTripTest {

    private static final String MODELS = "/vertexai-gemini/v1/projects/mock-project/locations/us-central1/publishers/google/models/";

    // no project-id, location or Google credentials are configured
    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(WeatherAssistant.class, WeatherTool.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "vertexai-gemini")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "vertexai-gemini");

    @Inject
    ChatModel chatModel;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    MockModelServer mock;

    @Inject
    WeatherAssistant weatherAssistant;

    @RegisterAiService(tools = WeatherTool.class)
    public interface WeatherAssistant {

        String chat(String message);
    }

    @ApplicationScoped
    public static class WeatherTool {

        @Tool("the current weather in a city")
        public String getWeather(String city) {
            return "rainy in " + city;
        }
    }

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void textReplyWithPlaceholderIdentityAndMockToken() {
        mock.chat().reply("Hello from the mock");

        assertThat(chatModel.chat("hi")).isEqualTo("Hello from the mock");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo(MODELS + "gemini-2.5-flash:generateContent");
        assertThat(r.dialect()).isEqualTo("vertexai-gemini");
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-access-token");
        assertThat(r.asChat().modelName()).isEqualTo("gemini-2.5-flash");
        assertThat(r.asChat().messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("hi");
        });
    }

    @Test
    void theMockTokenProviderReplacesApplicationDefaultCredentials() {
        // the client only falls back to Google Application Default Credentials when this resolves to nothing
        assertThat(ModelAuthProvider.resolve(NamedConfigUtil.DEFAULT_NAME))
                .containsInstanceOf(MockVertexAiGeminiAuthProvider.class);
        // qualified with the config name only: an unqualified lookup, as a provider with no config name makes, sees none
        assertThat(ModelAuthProvider.resolve(null)).isEmpty();
    }

    @Test
    void usageAndFinishReason() {
        mock.chat().reply(ChatReply.text("x").usage(14, 47));

        ChatResponse r = chatModel.chat(ChatRequest.builder().messages(UserMessage.from("hi")).build());

        assertThat(r.tokenUsage().inputTokenCount()).isEqualTo(14);
        assertThat(r.tokenUsage().outputTokenCount()).isEqualTo(47);
        assertThat(r.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    @ActivateRequestContext
    void toolCallThenAnswer() {
        mock.chat().replies(List.of(ChatReply.toolCall("getWeather", Map.of("city", "Paris")), ChatReply.text("Sunny")));

        assertThat(weatherAssistant.chat("weather in Paris?")).isEqualTo("Sunny");

        List<CapturedRequest> requests = mock.chat().requests();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).asChat().tools()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("getWeather");
            assertThat(t.parametersSchema().path("properties").has("city")).isTrue();
        });
        List<ChatRequestView.Message> second = requests.get(1).asChat().messages();
        assertThat(second).anySatisfy(m -> {
            assertThat(m.role()).isEqualTo("assistant");
            assertThat(m.toolCalls()).singleElement().satisfies(tc -> {
                assertThat(tc.name()).isEqualTo("getWeather");
                assertThat(tc.argumentsJson()).contains("Paris");
            });
        });
        assertThat(second).anySatisfy(m -> {
            assertThat(m.role()).isEqualTo("tool");
            assertThat(m.name()).isEqualTo("getWeather");
            assertThat(m.text()).contains("rainy in Paris");
        });
    }

    @Test
    void errorSurfacesAsException() {
        mock.chat().fail(400, "bad prompt");

        assertThatThrownBy(() -> chatModel.chat("hi")).hasMessageContaining("400");
        // the client does not retry
        assertThat(mock.chat().requests()).hasSize(1);
    }

    @Test
    void embeddingsOnePredictPerSegment() {
        mock.embeddings().replies(List.of(EmbeddingReply.of(new float[] { 1, 0, 0, 0 }),
                EmbeddingReply.of(new float[] { 0, 1, 0, 0 })));

        List<Embedding> embeddings = embeddingModel.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b")))
                .content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).containsExactly(1, 0, 0, 0);
        assertThat(embeddings.get(1).vector()).containsExactly(0, 1, 0, 0);
        List<CapturedRequest> requests = mock.embeddings().requests();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).path()).isEqualTo(MODELS + "text-embedding-004:predict");
        assertThat(requests.get(0).header("Authorization")).isEqualTo("Bearer mock-access-token");
        assertThat(requests.get(1).bodyAsJson().at("/instances/0/content").asText()).isEqualTo("b");
    }
}
