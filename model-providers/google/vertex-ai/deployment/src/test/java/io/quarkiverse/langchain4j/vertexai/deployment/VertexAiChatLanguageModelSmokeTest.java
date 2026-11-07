package io.quarkiverse.langchain4j.vertexai.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkiverse.langchain4j.vertexai.runtime.VertexAiChatLanguageModel;
import io.quarkiverse.langchain4j.vertexai.runtime.VertxAiRestApi;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class VertexAiChatLanguageModelSmokeTest extends WiremockAware {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "chat-bison";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.log-requests", "true");

    @Inject
    ChatModel chatLanguageModel;

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(chatLanguageModel)).isInstanceOf(VertexAiChatLanguageModel.class);

        wiremock().register(
                post(urlEqualTo(
                        String.format("/v1/projects/dummy/locations/dummy/publishers/google/models/%s:predict", CHAT_MODEL_ID)))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "predictions": [
                                            {
                                              "safetyAttributes": [
                                                {
                                                  "blocked": false,
                                                  "categories": [
                                                    "Death, Harm & Tragedy",
                                                    "Finance",
                                                    "Insult",
                                                    "Legal",
                                                    "Politics",
                                                    "Sexual"
                                                  ],
                                                  "safetyRatings": [
                                                    {
                                                      "category": "Dangerous Content",
                                                      "probabilityScore": 0.2,
                                                      "severityScore": 0,
                                                      "severity": "NEGLIGIBLE"
                                                    },
                                                    {
                                                      "severityScore": 0,
                                                      "category": "Harassment",
                                                      "severity": "NEGLIGIBLE",
                                                      "probabilityScore": 0.1
                                                    },
                                                    {
                                                      "category": "Hate Speech",
                                                      "severity": "NEGLIGIBLE",
                                                      "severityScore": 0.1,
                                                      "probabilityScore": 0
                                                    },
                                                    {
                                                      "probabilityScore": 0.1,
                                                      "severity": "NEGLIGIBLE",
                                                      "category": "Sexually Explicit",
                                                      "severityScore": 0
                                                    }
                                                  ],
                                                  "scores": [
                                                    0.1,
                                                    1,
                                                    0.1,
                                                    0.1,
                                                    0.3,
                                                    0.1
                                                  ]
                                                }
                                              ],
                                              "groundingMetadata": [
                                                {}
                                              ],
                                              "candidates": [
                                                {
                                                  "content": "Nice to meet you",
                                                  "author": "1"
                                                }
                                              ],
                                              "citationMetadata": [
                                                {
                                                  "citations": [
                                                  ]
                                                }
                                              ]
                                            }
                                          ],
                                          "metadata": {
                                            "tokenMetadata": {
                                              "inputTokenCount": {
                                                "totalBillableCharacters": 45,
                                                "totalTokens": 11
                                              },
                                              "outputTokenCount": {
                                                "totalBillableCharacters": 158,
                                                "totalTokens": 37
                                              }
                                            }
                                          }
                                        }
                                        """)));

        String response = chatLanguageModel.chat("hello");
        assertThat(response).isEqualTo("Nice to meet you");

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("Quarkus REST Client");
        String requestBody = new String(loggedRequest.getBody());
        assertThat(requestBody).contains("hello");
    }

    @Singleton
    public static class DummyAuthProvider implements VertxAiRestApi.AuthProvider {
        @Override
        public String getBearerToken() {
            return API_KEY;
        }
    }

}
