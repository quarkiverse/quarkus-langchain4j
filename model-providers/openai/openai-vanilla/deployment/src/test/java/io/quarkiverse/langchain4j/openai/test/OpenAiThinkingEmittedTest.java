package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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

import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.OnThinking;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.runtime.aiservice.ThinkingEmitted;
import io.quarkus.test.QuarkusExtensionTest;

public class OpenAiThinkingEmittedTest {

    static final List<ThinkingEmitted> EVENTS = new ArrayList<>();

    @ApplicationScoped
    public static class ReturnThinkingCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.returnThinking(true);
        }
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ThinkingAssistant.class, ReturnThinkingCustomizer.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever");

    @Inject
    ThinkingAssistant assistant;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void clearEvents() {
        EVENTS.clear();
        mock.reset();
    }

    @Test
    @ActivateRequestContext
    void invokesStaticHandlerWhenResponseCarriesThinking() {
        stubChatWithThinking();

        String answer = assistant.solve("What is 2+2?");
        assertThat(answer).isEqualTo("4");

        assertThat(EVENTS).hasSize(1);
        ThinkingEmitted event = EVENTS.get(0);
        assertThat(event.text()).isEqualTo("Let me compute: 2+2 = 4.");
        assertThat(event.methodName()).isEqualTo("solve");
        assertThat(event.serviceClass()).isEqualTo(ThinkingAssistant.class);
        assertThat(event.emittedAt()).isNotNull();

        assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.requests().get(0);
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.query()).isNull();
    }

    private void stubChatWithThinking() {
        // the mock does not render ChatReply.thinking for chat completions: the reasoning_content fixture is served as is
        mock.raw().on("POST", "/v1/chat/completions").replies(RawResponse.of(200,
                Map.of("Content-Type", "application/json"),
                """
                        {
                          "id": "chatcmpl-thinking",
                          "object": "chat.completion",
                          "created": 1733923283,
                          "model": "gpt-4o-mini",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "4",
                                "reasoning_content": "Let me compute: 2+2 = 4."
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 12,
                            "completion_tokens": 8,
                            "total_tokens": 20
                          }
                        }
                        """));
    }

    @RegisterAiService
    public interface ThinkingAssistant {
        String solve(String input);

        @OnThinking
        static void onThinking(ThinkingEmitted event) {
            EVENTS.add(event);
        }
    }
}
