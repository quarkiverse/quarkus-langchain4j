package io.quarkiverse.langchain4j.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URISyntaxException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.ai4j.openai4j.OpenAiHttpException;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import io.quarkiverse.langchain4j.openai.OpenAiApiException;
import io.quarkiverse.langchain4j.openai.OpenAiRestApi;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.QuarkusUnitTest;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OpenAiRestApiSmokeTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));
    private static final String TOKEN = "whatever";
    private static final String ORGANIZATION = "org";

    @Test
    @Order(1)
    void happyPath() throws URISyntaxException {
        OpenAiRestApi restApi = createClient();

        ChatCompletionResponse response = restApi.blockingChatCompletion(
                ChatCompletionRequest.builder().addUserMessage("test").build(),
                OpenAiRestApi.ApiMetadata.builder().openAiApiKey(TOKEN).organizationId(ORGANIZATION).build());
        assertThat(response).isNotNull();

        wiremock().verifyThat(
                postRequestedFor(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                        .withHeader("OpenAI-Organization", equalTo(ORGANIZATION)));
    }

    @Test
    @Order(2)
    void server200ButAPIError() throws URISyntaxException {
        wiremock().register(
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

        OpenAiRestApi restApi = createClient();

        assertThatThrownBy(() -> restApi.blockingChatCompletion(ChatCompletionRequest.builder().build(),
                OpenAiRestApi.ApiMetadata.builder().openAiApiKey(TOKEN).build()))
                .isInstanceOf(
                        OpenAiApiException.class);
    }

    @Test
    @Order(3)
    void server500() throws URISyntaxException {
        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                        .willReturn(aResponse()
                                .withStatus(500)
                                .withBody("This is a dummy error message")));

        OpenAiRestApi restApi = createClient();

        assertThatThrownBy(() -> restApi.blockingChatCompletion(ChatCompletionRequest.builder().build(),
                OpenAiRestApi.ApiMetadata.builder().openAiApiKey(TOKEN).build()))
                .isInstanceOf(
                        OpenAiHttpException.class)
                .hasMessage("This is a dummy error message");

        wiremock().verifyThat(
                postRequestedFor(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                        .withoutHeader("OpenAI-Organization"));
    }

    private OpenAiRestApi createClient() throws URISyntaxException {
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(new URI(resolvedWiremockUrl("v1")))
                .build(OpenAiRestApi.class);
    }
}
