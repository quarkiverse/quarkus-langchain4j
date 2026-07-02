package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class OpenAiResponsesApiCustomizerTest extends OpenAiBaseTest {

    private static final String RESPONSES_PATH = "/v1/responses";
    private static final String CUSTOMIZED_MODEL = "customized-responses-model";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(ResponsesChatModelCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.api", "responses");

    @Inject
    ChatModel chatModel;

    @BeforeEach
    void reset() {
        resetRequests();
        wiremock().resetMappings();
    }

    @Test
    void appliesModelBuilderCustomizerOnResponsesPath() throws IOException {
        stubBlockingResponse();

        chatModel.chat("hello");

        var requestAsMap = getRequestAsMap();
        assertThat(requestAsMap).containsEntry("model", CUSTOMIZED_MODEL);
    }

    private void stubBlockingResponse() {
        wiremock().register(
                post(urlEqualTo(RESPONSES_PATH))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "id": "resp_test",
                                          "object": "response",
                                          "created_at": 1733923283,
                                          "model": "%s",
                                          "status": "completed",
                                          "output": [
                                            {
                                              "type": "message",
                                              "content": [
                                                {
                                                  "type": "output_text",
                                                  "text": "hi"
                                                }
                                              ]
                                            }
                                          ],
                                          "usage": {
                                            "input_tokens": 10,
                                            "output_tokens": 5,
                                            "total_tokens": 15
                                          }
                                        }
                                        """.formatted(CUSTOMIZED_MODEL))));
    }

    @Singleton
    public static class ResponsesChatModelCustomizer
            implements ModelBuilderCustomizer<OpenAiResponsesChatModel.Builder> {

        @Override
        public void customize(OpenAiResponsesChatModel.Builder builder) {
            builder.modelName(CUSTOMIZED_MODEL);
        }
    }
}
