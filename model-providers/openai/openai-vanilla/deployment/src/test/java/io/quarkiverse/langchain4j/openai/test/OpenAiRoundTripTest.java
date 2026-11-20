package io.quarkiverse.langchain4j.openai.test;

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
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class OpenAiRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(WeatherAssistant.class, WeatherTool.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "openai");

    @Inject
    ChatModel chatModel;

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
    void textReply() {
        mock.chat().reply("Hello from the mock");

        assertThat(chatModel.chat("hi")).isEqualTo("Hello from the mock");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).endsWith("/v1/chat/completions");
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-api-key");
        assertThat(r.asChat().messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("hi");
        });
    }

    @Test
    void defaultReplyWhenNothingQueued() {
        assertThat(chatModel.chat("hi")).isEqualTo("This is a mock reply from the Quarkus LangChain4j mock model API.");
    }

    @Test
    @ActivateRequestContext
    void toolCallThenAnswer() {
        mock.chat().replies(List.of(ChatReply.toolCall("getWeather", Map.of("city", "Paris")), ChatReply.text("Sunny")));

        // the AI service runs the whole tool loop: tool call, tool execution, second request, final answer
        assertThat(weatherAssistant.chat("weather in Paris?")).isEqualTo("Sunny");

        List<CapturedRequest> requests = mock.chat().requests();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).asChat().tools()).singleElement()
                .extracting(ChatRequestView.ToolDefinition::name).isEqualTo("getWeather");
        assertThat(requests.get(1).asChat().messages()).anySatisfy(m -> {
            assertThat(m.role()).isEqualTo("tool");
            assertThat(m.text()).isEqualTo("rainy in Paris");
        });
    }

    @Test
    void usageAndFinishReason() {
        mock.chat().reply(ChatReply.text("x").usage(14, 47));

        ChatResponse r = chatModel.chat(ChatRequest.builder().messages(UserMessage.from("hi")).build());

        assertThat(r.tokenUsage().totalTokenCount()).isEqualTo(61);
        assertThat(r.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void errorSurfacesAsException() {
        // the OpenAI client retries a 429 once by default, so both attempts must fail
        mock.chat().fail(429, "rate limited");
        mock.chat().fail(429, "rate limited");

        assertThatThrownBy(() -> chatModel.chat("hi")).hasMessageContaining("rate limited");
    }

    @Test
    void unmatchedPathSurfacesAsException() {
        mock.raw().on("POST", "/v1/chat/completions").reply(404, "{\"error\":\"nope\"}");

        assertThatThrownBy(() -> chatModel.chat("hi")).hasMessageContaining("nope");
    }
}
