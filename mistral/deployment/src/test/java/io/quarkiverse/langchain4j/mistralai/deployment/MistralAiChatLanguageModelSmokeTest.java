package io.quarkiverse.langchain4j.mistralai.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

class MistralAiChatLanguageModelSmokeTest {
    private static final int WIREMOCK_PORT = 8089;
    private static final String CHAT_MODEL_ID = "mistral-tiny";
    private static final String API_KEY = "somekey";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.base-url", "http://localhost:" + WIREMOCK_PORT + "/v1");

    static WireMockServer wireMockServer;

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT));
        wireMockServer.start();
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
    }

    @Inject
    ChatLanguageModel chatLanguageModel;

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(chatLanguageModel)).isInstanceOf(MistralAiChatModel.class);

        wireMockServer.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                            {
                                              "id": "0bdf265cb18d493d96b62029f024d897",
                                              "object": "chat.completion",
                                              "created": 1711442725,
                                              "model": "mistral-tiny",
                                              "choices": [
                                                {
                                                  "index": 0,
                                                  "message": {
                                                    "role": "assistant",
                                                    "content": "Nice to meet you"
                                                  },
                                                  "finish_reason": "stop"
                                                }
                                              ],
                                              "usage": {
                                                "prompt_tokens": 19,
                                                "total_tokens": 127,
                                                "completion_tokens": 108
                                              }
                                            }
                                        """)));

        String response = chatLanguageModel.generate("hello");
        assertThat(response).isEqualTo("Nice to meet you");

        assertThat(wireMockServer.getAllServeEvents()).hasSize(1);
        ServeEvent serveEvent = wireMockServer.getAllServeEvents().get(0); // this works because we reset requests for Wiremock before each test
        LoggedRequest loggedRequest = serveEvent.getRequest();
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("Resteasy Reactive Client");
        String requestBody = new String(loggedRequest.getBody());
        assertThat(requestBody).contains("hello").contains(CHAT_MODEL_ID);
    }

}
