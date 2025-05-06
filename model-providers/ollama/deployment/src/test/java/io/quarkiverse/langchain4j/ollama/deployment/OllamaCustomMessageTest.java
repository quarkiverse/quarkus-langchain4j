package io.quarkiverse.langchain4j.ollama.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class OllamaCustomMessageTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.ollama.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-name", "granite3-guardian")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-responses", "true");

    @Inject
    ChatModel chatLanguageModel;

    @Test
    void extract() {
        wiremock().register(
                post(urlEqualTo("/api/chat"))
                        .withRequestBody(equalToJson(
                                """
                                        {
                                            "model": "granite3-guardian",
                                            "messages": [
                                                {
                                                    "role": "system",
                                                    "content": "context_relevance"
                                                },
                                                {
                                                    "role": "user",
                                                    "content": "What is the history of treaty making?"
                                                },
                                                {
                                                    "role": "context",
                                                    "content": "One significant part of treaty making is that signing a treaty implies recognition that the other side is a sovereign state and that the agreement being considered is enforceable under international law. Hence, nations can be very careful about terming an agreement to be a treaty. For example, within the United States, agreements between states are compacts and agreements between states and the federal government or between agencies of the government are memoranda of understanding."
                                                }
                                            ],
                                            "options": {
                                                "temperature": 0.8,
                                                "top_k": 40,
                                                "top_p": 0.9
                                            },
                                            "stream": false,
                                            "tools" : [ ]
                                        }"""))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                            "model": "granite3-guardian",
                                            "created_at": "2025-01-28T15:21:23.422542932Z",
                                            "message": {
                                                "role": "assistant",
                                                "content": "Yes"
                                            },
                                            "done_reason": "stop",
                                            "done": true,
                                            "total_duration": 8125806496,
                                            "load_duration": 4223887064,
                                            "prompt_eval_count": 31,
                                            "prompt_eval_duration": 1331000000,
                                            "eval_count": 2,
                                            "eval_duration": 2569000000
                                        }""")));

        String retrievedContext = "One significant part of treaty making is that signing a treaty implies recognition that the other side is a sovereign state and that the agreement being considered is enforceable under international law. Hence, nations can be very careful about terming an agreement to be a treaty. For example, within the United States, agreements between states are compacts and agreements between states and the federal government or between agencies of the government are memoranda of understanding.";

        List<ChatMessage> messages = List.of(
                SystemMessage.from("context_relevance"),
                UserMessage.from("What is the history of treaty making?"),
                CustomMessage.from(Map.of("role", "context", "content", retrievedContext)));

        ChatResponse chatResponse = chatLanguageModel.chat(ChatRequest.builder().messages(messages).build());
        assertThat(chatResponse.aiMessage().text()).isEqualTo("Yes");
    }
}
