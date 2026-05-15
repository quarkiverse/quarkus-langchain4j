package io.quarkiverse.langchain4j.ollama.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.Thinking;
import io.quarkiverse.langchain4j.runtime.aiservice.ThinkingEmitted;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class OllamaThinkingEmittedTest extends WiremockAware {

    static final List<ThinkingEmitted> EVENTS = new ArrayList<>();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ThinkingAssistant.class))
            .overrideConfigKey("quarkus.langchain4j.ollama.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-name", "qwen3:1.7b")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.think", "true")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.return-thinking", "true")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false");

    @Inject
    ThinkingAssistant assistant;

    @BeforeEach
    void clearEvents() {
        EVENTS.clear();
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
    }

    private void stubChatWithThinking() {
        wiremock().register(
                post(urlEqualTo("/api/chat"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "model": "qwen3:1.7b",
                                          "created_at": "2024-12-11T15:21:23.422542932Z",
                                          "message": {
                                            "role": "assistant",
                                            "content": "4",
                                            "thinking": "Let me compute: 2+2 = 4."
                                          },
                                          "done_reason": "stop",
                                          "done": true
                                        }
                                        """)));
    }

    @RegisterAiService
    public interface ThinkingAssistant {
        String solve(String input);

        @Thinking
        static void onThinking(ThinkingEmitted event) {
            EVENTS.add(event);
        }
    }
}
