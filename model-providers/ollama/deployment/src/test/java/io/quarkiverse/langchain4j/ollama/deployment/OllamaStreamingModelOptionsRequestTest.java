package io.quarkiverse.langchain4j.ollama.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class OllamaStreamingModelOptionsRequestTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.ollama.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.model-name", "qwen3:1.7b")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.options.think", "true")
            .overrideConfigKey("quarkus.langchain4j.ollama.chat-model.options.seed", "42")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false");

    @Singleton
    @RegisterAiService
    interface StreamingService {
        Multi<String> streaming(@dev.langchain4j.service.UserMessage String text);
    }

    @Inject
    StreamingService streamingService;

    @Test
    void shouldSendOptionsInStreamingRequest() {
        wiremock().register(
                post(urlEqualTo("/api/chat"))
                        .withRequestBody(equalToJson("""
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
                                """))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/x-ndjson")
                                .withBody(
                                        """
                                                {"model":"qwen3:1.7b","created_at":"2024-12-11T15:21:23.422542932Z","message":{"role":"assistant","content":"Hello"},"done":false}
                                                {"model":"qwen3:1.7b","created_at":"2024-12-11T15:21:23.522542932Z","message":{"role":"assistant","content":"!"},"done":false}
                                                {"model":"qwen3:1.7b","created_at":"2024-12-11T15:21:24.109059873Z","message":{"role":"assistant","content":""},"done_reason":"stop","done":true}
                                                """)));

        List<String> result = streamingService.streaming("Say hello").collect().asList().await().indefinitely();
        assertThat(result).containsExactly("Hello", "!");
    }
}
