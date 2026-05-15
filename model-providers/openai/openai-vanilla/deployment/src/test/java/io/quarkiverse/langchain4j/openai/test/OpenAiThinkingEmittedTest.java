package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

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
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.runtime.aiservice.ThinkingEmitted;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class OpenAiThinkingEmittedTest extends OpenAiBaseTest {

    static final List<ThinkingEmitted> EVENTS = new ArrayList<>();

    @ApplicationScoped
    public static class ReturnThinkingCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.returnThinking(true);
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ThinkingAssistant.class, ReturnThinkingCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ThinkingAssistant assistant;

    @BeforeEach
    void clearEvents() {
        EVENTS.clear();
        wiremock().resetMappings();
        wiremock().resetRequests();
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
                post(urlEqualTo("/v1/chat/completions"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
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
                                        """)));
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
