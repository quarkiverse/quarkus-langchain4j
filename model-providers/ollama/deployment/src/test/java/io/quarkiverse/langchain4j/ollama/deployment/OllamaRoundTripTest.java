package io.quarkiverse.langchain4j.ollama.deployment;

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

public class OllamaRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(WeatherAssistant.class, WeatherTool.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "ollama");

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
    void textReplyWithoutCredentials() {
        mock.chat().reply("Hello from the mock");

        assertThat(chatModel.chat("hi")).isEqualTo("Hello from the mock");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/ollama/api/chat");
        assertThat(r.dialect()).isEqualTo("ollama");
        assertThat(r.header("Authorization")).isNull();
        assertThat(r.asChat().stream()).isFalse();
        assertThat(r.asChat().modelName()).isEqualTo("llama3.2");
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

        // the AI service runs the whole tool loop: tool_calls, tool execution, tool message request, final answer
        assertThat(weatherAssistant.chat("weather in Paris?")).isEqualTo("Sunny");

        List<CapturedRequest> requests = mock.chat().requests();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).asChat().tools()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("getWeather");
            assertThat(t.parametersSchema().path("properties").has("city")).isTrue();
        });
        // the client sends the assistant's arguments back as an object, as the docs require
        assertThat(requests.get(1).bodyAsJson().at("/messages/1/tool_calls/0/function/arguments/city").asText())
                .isEqualTo("Paris");
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
            assertThat(m.text()).isEqualTo("rainy in Paris");
        });
    }

    @Test
    void errorSurfacesAsException() {
        mock.chat().fail(400, "bad request");

        assertThatThrownBy(() -> chatModel.chat("hi")).hasMessageContaining("bad request");
        assertThat(mock.chat().requests()).hasSize(1);
    }

    @Test
    void serverErrorIsRetriedOnce() {
        // the recorder sets max-retries to 1, and a 5xx is retriable
        mock.chat().fail(500, "boom");
        mock.chat().fail(500, "boom");

        assertThatThrownBy(() -> chatModel.chat("hi")).hasMessageContaining("boom");
        assertThat(mock.chat().requests()).hasSize(2);
    }
}
