package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URISyntaxException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.WireMockServer;

import dev.ai4j.openai4j.OpenAiHttpException;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import io.quarkiverse.langchain4j.openai.OpenAiApiException;
import io.quarkiverse.langchain4j.openai.OpenAiRestApi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.QuarkusUnitTest;

public class OpenAiRestApiSmokeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WiremockUtils.class));
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
        wireMockServer.stubFor(WiremockUtils.defaultChatCompletionsStub(TOKEN));

        OpenAiRestApi restApi = createClient();

        ChatCompletionResponse response = restApi.blockingChatCompletion(ChatCompletionRequest.builder().build(), TOKEN, null);
        assertThat(response).isNotNull();
    }

    @Test
    void server500() throws URISyntaxException {
        wireMockServer.stubFor(
                WiremockUtils.chatCompletionMapping(TOKEN)
                        .willReturn(aResponse()
                                .withStatus(500)
                                .withBody("This is a dummy error message")));

        OpenAiRestApi restApi = createClient();

        assertThatThrownBy(() -> restApi.blockingChatCompletion(ChatCompletionRequest.builder().build(), TOKEN, null))
                .isInstanceOf(
                        OpenAiHttpException.class)
                .hasMessage("This is a dummy error message");
    }

    @Test
    void server200ButAPIError() throws URISyntaxException {
        wireMockServer.stubFor(
                WiremockUtils.chatCompletionMapping(TOKEN)
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

        OpenAiRestApi restApi = createClient();

        assertThatThrownBy(() -> restApi.blockingChatCompletion(ChatCompletionRequest.builder().build(), TOKEN, null))
                .isInstanceOf(
                        OpenAiApiException.class);
    }

    private OpenAiRestApi createClient() throws URISyntaxException {
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(new URI("http://localhost:8089/v1"))
                .build(OpenAiRestApi.class);
    }
}
