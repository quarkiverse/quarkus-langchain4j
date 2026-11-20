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

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;
import tools.jackson.databind.ObjectMapper;

public class OllamaStreamingModelOptionsRequestTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-name", "qwen3:1.7b")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.think", "true")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.return-thinking", "true")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.num-ctx", "4096")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.repeat-last-n", "64")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.repeat-penalty", "1.2")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.mirostat", "2")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.mirostat-eta", "0.2")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.mirostat-tau", "4.0")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-options.min-p", "0.05")
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
                    "seed" : 42,
                    "num_ctx" : 4096,
                    "repeat_last_n" : 64,
                    "repeat_penalty" : 1.2,
                    "mirostat" : 2,
                    "mirostat_eta" : 0.2,
                    "mirostat_tau" : 4.0,
                    "min_p" : 0.05
                  },
                  "think" : true,
                  "stream" : true
                }
                """));
    }
}
