package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;

public class OllamaStreamingModelOptionsRequestTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-name", "qwen3:1.7b")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.think", "true")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.seed", "42")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false");

    @Singleton
    @RegisterAiService
    interface StreamingService {
        Multi<String> streaming(@dev.langchain4j.service.UserMessage String text);
    }

    @Inject
    StreamingService streamingService;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void shouldSendOptionsInStreamingRequest() throws Exception {
        mock.chat().reply(ChatReply.text("Hello!").chunks(List.of("Hello", "!")));

        List<String> result = streamingService.streaming("Say hello").collect().asList().await().indefinitely();
        assertThat(result).containsExactly("Hello", "!");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/ollama/api/chat");
        assertThat(request.bodyAsJson()).isEqualTo(new ObjectMapper().readTree("""
                {
                  "model" : "qwen3:1.7b",
                  "messages" : [ {
                    "role" : "user",
                    "content" : "Say hello"
                  } ],
                  "options" : {
                    "temperature" : 0.8,
                    "top_k" : 40,
                    "top_p" : 0.9,
                    "think" : true,
                    "seed" : 42
                  },
                  "stream" : true
                }
                """));
    }
}
