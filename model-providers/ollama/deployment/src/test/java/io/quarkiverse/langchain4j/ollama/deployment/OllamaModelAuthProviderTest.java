package io.quarkiverse.langchain4j.ollama.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class OllamaModelAuthProviderTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.ollama.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-responses", "true");

    @Inject
    ChatLanguageModel chatLanguageModel;

    @Test
    void blocking() {
        assertThat(ClientProxy.unwrap(chatLanguageModel)).isInstanceOf(OllamaChatModel.class);

        wiremock().register(
                post(urlEqualTo("/api/chat"))
                        .withRequestBody(matchingJsonPath("$.model", equalTo("llama3.2")))
                        .withHeader("Authorization", equalTo("Bearer test"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "model": "llama3.2",
                                          "created_at": "2024-05-03T10:27:56.84235715Z",
                                          "message": {
                                            "role": "assistant",
                                            "content": "Nice to meet you"
                                          },
                                          "done": true,
                                          "total_duration": 1206200561,
                                          "load_duration": 695039,
                                          "prompt_eval_duration": 18430000,
                                          "eval_count": 105,
                                          "eval_duration": 1057198000
                                        }
                                        """)));

        String response = chatLanguageModel.chat("hello");
        assertThat(response).isEqualTo("Nice to meet you");
    }

    @Singleton
    public static class OllamaClientAuthHeaderFilter implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer test";
        }
    }
}
