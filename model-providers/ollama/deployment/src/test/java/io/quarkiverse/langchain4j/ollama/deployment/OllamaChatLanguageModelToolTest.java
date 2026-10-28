package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

class OllamaChatLanguageModelToolTest {

    private static boolean called;

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-responses", "true");

    @Inject
    ChatModel chatLanguageModel;

    @Inject
    Bot bot;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setup() {
        mock.reset();
        called = false;
    }

    @Test
    @ActivateRequestContext
    void doesFilterHallucinatedExecutionRequestAndCallPresentTool() {
        mock.chat().replies(List.of(
                ChatReply.text("I do not know the current time, I am sorry")
                        .addToolCall("getTime", "{}")
                        .addToolCall("getName", "{}"),
                ChatReply.text("It is 13:00")));

        var response = bot.chat("What time is it?");

        assertThat(response).isEqualTo("It is 13:00");
        assertThat(called).isTrue();

        assertThat(mock.chat().requests()).hasSize(2).allSatisfy(r -> {
            assertThat(r.path()).isEqualTo("/ollama/api/chat");
            assertThat(r.bodyAsJson().at("/model").asText()).isEqualTo("llama3.2");
            assertThat(r.header("Authorization")).isNull();
        });
        // the second request carries the result of the tool the first reply asked for
        assertThat(mock.chat().requests().get(1).body()).contains("13:00");
    }

    @RegisterAiService(tools = MyTool.class, toolHallucinationStrategy = MyHallucinationStrategy.class)
    interface Bot {
        String chat(String message);
    }

    @ApplicationScoped
    public static class MyTool {

        @Tool("getTime")
        public String getTime() {
            called = true;
            return "13:00";
        }
    }

    @ApplicationScoped
    public static class MyHallucinationStrategy implements Function<ToolExecutionRequest, ToolExecutionResultMessage> {

        @Override
        public ToolExecutionResultMessage apply(ToolExecutionRequest request) {
            return ToolExecutionResultMessage.from(request, "no tool named '" + request.name() + "' found");
        }
    }
}
