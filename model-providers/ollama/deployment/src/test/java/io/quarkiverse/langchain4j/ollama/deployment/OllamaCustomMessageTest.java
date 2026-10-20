package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class OllamaCustomMessageTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-name", "granite3-guardian")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-responses", "true");

    @Inject
    ChatModel chatLanguageModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void extract() throws Exception {
        mock.chat().reply("Yes");

        String retrievedContext = "One significant part of treaty making is that signing a treaty implies recognition that the other side is a sovereign state and that the agreement being considered is enforceable under international law. Hence, nations can be very careful about terming an agreement to be a treaty. For example, within the United States, agreements between states are compacts and agreements between states and the federal government or between agencies of the government are memoranda of understanding.";

        List<ChatMessage> messages = List.of(
                SystemMessage.from("context_relevance"),
                UserMessage.from("What is the history of treaty making?"),
                CustomMessage.from(Map.of("role", "context", "content", retrievedContext)));

        ChatResponse chatResponse = chatLanguageModel.chat(ChatRequest.builder().messages(messages).build());
        assertThat(chatResponse.aiMessage().text()).isEqualTo("Yes");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/ollama/api/chat");
        assertThat(request.bodyAsJson()).isEqualTo(new ObjectMapper().readTree(
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
                                "top_p": 0.9,
                                "stop" : [ ]
                            },
                            "stream": false,
                            "tools" : [ ]
                        }"""));
    }
}
