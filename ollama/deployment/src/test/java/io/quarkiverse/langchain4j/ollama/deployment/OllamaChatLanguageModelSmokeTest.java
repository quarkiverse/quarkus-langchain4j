package io.quarkiverse.langchain4j.ollama.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.ollama.OllamaChatLanguageModel;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class OllamaChatLanguageModelSmokeTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            String.format("quarkus.langchain4j.ollama.base-url=%s", WiremockAware.wiremockUrlForConfig())),
                            "application.properties"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-responses", "true");

    @Inject
    ChatLanguageModel chatLanguageModel;

    @Test
    void blocking() {
        assertThat(ClientProxy.unwrap(chatLanguageModel)).isInstanceOf(OllamaChatLanguageModel.class);

        wiremock().register(
                post(urlEqualTo("/api/chat"))
                        .withRequestBody(matchingJsonPath("$.model", equalTo("llama3")))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "model": "llama3",
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

        String response = chatLanguageModel.generate("hello");
        assertThat(response).isEqualTo("Nice to meet you");

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("Resteasy Reactive Client");
        String requestBody = new String(loggedRequest.getBody());
        assertThat(requestBody).contains("hello");
    }
}
