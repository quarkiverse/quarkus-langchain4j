package io.quarkiverse.langchain4j.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URISyntaxException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;

import dev.ai4j.openai4j.OpenAiHttpException;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import io.quarkiverse.langchain4j.QuarkusRestApi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.QuarkusUnitTest;

public class QuarkusRestApiSmokeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));
    private static final String TOKEN = "whatever";

    static WireMockServer wireMockServer;

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(8089));
        wireMockServer.start();
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @Test
    void happyPath() throws URISyntaxException {
        wireMockServer.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "id": "cmpl-uqkvlQyYK7bGYrRHQ0eXlWi7",
                                          "object": "text_completion",
                                          "created": 1589478378,
                                          "model": "gpt-3.5-turbo-instruct",
                                          "choices": [
                                            {
                                              "text": "nThis is indeed a test",
                                              "index": 0,
                                              "logprobs": null,
                                              "finish_reason": "length"
                                            }
                                          ],
                                          "usage": {
                                            "prompt_tokens": 5,
                                            "completion_tokens": 7,
                                            "total_tokens": 12
                                          }
                                        }
                                        """)));

        QuarkusRestApi restApi = createClient();

        ChatCompletionResponse response = restApi.blockingChatCompletion(ChatCompletionRequest.builder().build(), TOKEN);
        assertThat(response).isNotNull();
    }

    @Test
    void server500() throws URISyntaxException {
        wireMockServer.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                        .willReturn(aResponse()
                                .withStatus(500)
                                .withBody("This is a dummy error message")));

        QuarkusRestApi restApi = createClient();

        assertThatThrownBy(() -> restApi.blockingChatCompletion(ChatCompletionRequest.builder().build(), TOKEN)).isInstanceOf(
                OpenAiHttpException.class).hasMessage("This is a dummy error message");
    }

    @Test
    @Disabled("Currently no exception is thrown and the completion object is just all nulls")
    // TODO: deal with this probably at the langchain4j level
    void server200ButAPIError() throws URISyntaxException {
        wireMockServer.stubFor(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        """
                                                {
                                                    "error": {
                                                        "message": "The server had an error while processing your request. Sorry about that!",
                                                        "type": "server_error",
                                                        "param": null,
                                                        "code": null
                                                    }
                                                }
                                                """)));

        QuarkusRestApi restApi = createClient();

        assertThatThrownBy(() -> restApi.blockingChatCompletion(ChatCompletionRequest.builder().build(), TOKEN));
    }

    private QuarkusRestApi createClient() throws URISyntaxException {
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(new URI("http://localhost:8089/v1"))
                .build(QuarkusRestApi.class);
    }
}
