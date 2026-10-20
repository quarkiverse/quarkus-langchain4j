package io.quarkiverse.langchain4j.ai.gemini.deployment;

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
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class AiGeminiRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(WeatherAssistant.class, WeatherTool.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "ai-gemini")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "ai-gemini");

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
    void textReplyWithPlaceholderApiKey() {
        mock.chat().reply("Hello from the mock");

        assertThat(chatModel.chat("hi")).isEqualTo("Hello from the mock");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/ai-gemini/v1beta/models/gemini-2.5-flash:generateContent");
        assertThat(r.dialect()).isEqualTo("ai-gemini");
        // no api-key is configured: the extension fills in the placeholder
        assertThat(r.header("x-goog-api-key")).isEqualTo("mock-api-key");
        assertThat(r.asChat().modelName()).isEqualTo("gemini-2.5-flash");
        assertThat(r.asChat().stream()).isFalse();
        assertThat(r.asChat().messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("hi");
        });
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

        // the AI service runs the whole tool loop: functionCall, tool execution, functionResponse request, final answer
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
        // LangChain4j does not retry a 400
        mock.chat().fail(400, "bad prompt");

        assertThatThrownBy(() -> chatModel.chat("hi")).hasMessageContaining("bad prompt");
        assertThat(mock.chat().requests()).hasSize(1);
    }

    @Test
    void embeddings() {
        mock.embeddings().reply(EmbeddingReply.of(new float[] { 1, 0, 0, 0 }, new float[] { 0, 1, 0, 0 }));

        List<Embedding> embeddings = embeddingModel.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b")))
                .content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).containsExactly(1, 0, 0, 0);
        assertThat(embeddings.get(1).vector()).containsExactly(0, 1, 0, 0);
        CapturedRequest r = mock.embeddings().singleRequest();
        assertThat(r.path()).isEqualTo("/ai-gemini/v1beta/models/text-embedding-004:batchEmbedContents");
        assertThat(r.bodyAsJson().at("/requests")).hasSize(2);
    }

    @Test
    void singleEmbedding() {
        mock.embeddings().reply(EmbeddingReply.of(new float[] { 1, 0, 0, 0 }));

        assertThat(embeddingModel.embed("a").content().vector()).containsExactly(1, 0, 0, 0);

        CapturedRequest r = mock.embeddings().singleRequest();
        assertThat(r.path()).isEqualTo("/ai-gemini/v1beta/models/text-embedding-004:embedContent");
        assertThat(r.bodyAsJson().at("/content/parts/0/text").asText()).isEqualTo("a");
    }
}
