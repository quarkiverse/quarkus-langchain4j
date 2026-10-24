package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class AiServiceAsToolBoxTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideConfigKey("quarkus.rest-client.rest-calculator.url", "http://localhost:${quarkus.http.test-port:8081}");

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
    }

    @Inject
    Bot bot;

    @Test
    @ActivateRequestContext
    void should_execute_tool_then_answer() {
        // nothing is scripted here: the guardrail scripts the answer, so a request sent before it ran would get the
        // mock's default reply instead
        String userMessage = "What is the weather in Rome?";

        String answer = bot.chat(userMessage);

        assertThat(answer).isEqualTo("Rome");

        assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.query()).isNull();
        assertThat(request.header("Authorization")).isEqualTo("Bearer whatever");
        assertThat(request.asChat().tools()).extracting(ChatRequestView.ToolDefinition::name)
                .containsExactly("extractCity");
    }

    @RegisterAiService
    interface Bot {
        @ToolBox(CityExtractorAgent.class)
        @InputGuardrails(SecondStateGuardrail.class)
        String chat(String message);
    }

    @RegisterAiService
    public interface CityExtractorAgent {
        @UserMessage("""
                You are given one question and you have to extract city name from it
                Only reply the city name if it exists or reply 'unknown_city' if there is no city name in question

                Here is the question: {question}
                """)
        @Tool("Extracts the city from a question")
        String extractCity(String question);
    }

    @ApplicationScoped
    public static class SecondStateGuardrail implements InputGuardrail {
        private final MockModelServer mock;

        public SecondStateGuardrail(MockModelServer mock) {
            this.mock = mock;
        }

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage userMessage) {
            mock.chat().reply("Rome");
            return success();
        }
    }
}
