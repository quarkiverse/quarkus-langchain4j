package io.quarkiverse.langchain4j.watsonx.deployment;

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
import io.quarkiverse.langchain4j.mockapi.ErrorReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class WatsonxRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(WeatherAssistant.class, WeatherTool.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "watsonx");

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
    void textReplyWithUsage() {
        mock.chat().reply(ChatReply.text("Hello from the mock").usage(14, 47));

        ChatResponse response = chatModel.chat(ChatRequest.builder().messages(UserMessage.from("hi")).build());

        assertThat(response.aiMessage().text()).isEqualTo("Hello from the mock");
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(14);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(47);
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/watsonx/ml/v1/text/chat");
        assertThat(r.dialect()).isEqualTo("watsonx");
        assertThat(r.query()).contains("version=");
        assertThat(r.asChat().stream()).isFalse();
        assertThat(r.asChat().modelName()).isEqualTo("ibm/granite-4-h-small");
        assertThat(r.asChat().messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("hi");
        });
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
        List<ChatRequestView.Message> second = requests.get(1).asChat().messages();
        assertThat(second).anySatisfy(m -> {
            assertThat(m.role()).isEqualTo("assistant");
            assertThat(m.toolCalls()).singleElement().satisfies(tc -> {
                assertThat(tc.id()).isEqualTo("call_mock_0");
                assertThat(tc.name()).isEqualTo("getWeather");
                assertThat(tc.argumentsJson()).contains("Paris");
            });
        });
        assertThat(second).anySatisfy(m -> {
            assertThat(m.role()).isEqualTo("tool");
            assertThat(m.toolCallId()).isEqualTo("call_mock_0");
            assertThat(m.text()).isEqualTo("rainy in Paris");
        });
    }

    @Test
    void clientErrorIsNotRetried() {
        mock.chat().fail(400, "bad request");

        assertThatThrownBy(() -> chatModel.chat("hi")).hasMessageContaining("bad request");
        // the client retries only 429, 502, 503, 504, 520 and an expired token
        assertThat(mock.chat().requests()).hasSize(1);
    }

    @Test
    void serviceUnavailableIsRetried() {
        mock.chat().replies(List.of(ErrorReply.of(503, "busy"), ChatReply.text("finally")));

        assertThat(chatModel.chat("hi")).isEqualTo("finally");
        assertThat(mock.chat().requests()).hasSize(2);
    }
}
