package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.decision.DecisionCreateRequest;
import io.quarkiverse.langchain4j.QuarkusHttpException;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.openai.common.OpenAiApiException;
import io.quarkiverse.langchain4j.openai.common.OpenAiRestApi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.QuarkusExtensionTest;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OpenAiRestApiSmokeTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            // about the Chat Completions API: without a base URL of its own the model would target the Responses API
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.mode", "chat-completion");
    private static final String TOKEN = "whatever";
    private static final String ORGANIZATION = "org";

    @Inject
    MockModelServer mock;

    @ConfigProperty(name = "quarkus.langchain4j.openai.base-url")
    String baseUrl;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    @Order(1)
    void happyPath() throws URISyntaxException {
        mock.chat().reply("whatever");
        OpenAiRestApi restApi = createClient();

        ChatCompletionResponse response = restApi.blockingChatCompletion(
                ChatCompletionRequest.builder().addUserMessage("test").build(),
                OpenAiRestApi.ApiMetadata.builder().openAiApiKey(TOKEN).organizationId(ORGANIZATION).build());
        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo("whatever");

        CapturedRequest request = singleChatCompletionsRequest();
        assertThat(request.header("OpenAI-Organization")).isEqualTo(ORGANIZATION);
    }

    @Test
    @Order(2)
    void server200ButAPIError() throws URISyntaxException {
        // a 200 response carrying an error payload: the neutral error reply always comes with an error status
        mock.raw().on("POST", "/v1/chat/completions").replies(RawResponse.of(200,
                Map.of("Content-Type", "application/json"),
                """
                        {
                            "error": {
                                "message": "The server had an error while processing your request. Sorry about that!",
                                "type": "server_error",
                                "param": null,
                                "code": null
                            }
                        }
                        """));

        OpenAiRestApi restApi = createClient();

        assertThatThrownBy(() -> restApi.blockingChatCompletion(ChatCompletionRequest.builder().build(),
                OpenAiRestApi.ApiMetadata.builder().openAiApiKey(TOKEN).build()))
                .isInstanceOf(
                        OpenAiApiException.class);

        singleChatCompletionsRequest();
    }

    @Test
    @Order(3)
    void server500() throws URISyntaxException {
        // a plain text error body, which the exception message must carry verbatim
        mock.raw().on("POST", "/v1/chat/completions").replies(RawResponse.of(500, Map.of(),
                "This is a dummy error message"));

        OpenAiRestApi restApi = createClient();

        assertThatThrownBy(() -> restApi.blockingChatCompletion(ChatCompletionRequest.builder().build(),
                OpenAiRestApi.ApiMetadata.builder().openAiApiKey(TOKEN).build()))
                .isInstanceOf(
                        HttpException.class)
                .hasMessage("This is a dummy error message");

        CapturedRequest request = singleChatCompletionsRequest();
        assertThat(request.header("OpenAI-Organization")).isNull();
    }

    @Test
    @Order(4)
    void server429ExposesResponseHeaders() throws URISyntaxException {
        // rate-limit headers on the error response
        mock.raw().on("POST", "/v1/chat/completions").replies(RawResponse.of(429,
                Map.of("Retry-After", "7", "retry-after-ms", "7000"),
                "Rate limit exceeded"));

        OpenAiRestApi restApi = createClient();

        Throwable thrown = catchThrowable(() -> restApi.blockingChatCompletion(ChatCompletionRequest.builder().build(),
                OpenAiRestApi.ApiMetadata.builder().openAiApiKey(TOKEN).build()));

        assertThat(thrown).isInstanceOf(QuarkusHttpException.class);
        QuarkusHttpException exception = (QuarkusHttpException) thrown;
        assertThat(exception.statusCode()).isEqualTo(429);
        assertThat(exception.header("retry-after")).isEqualTo(Optional.of("7"));
        assertThat(exception.header("retry-after-ms")).isEqualTo(Optional.of("7000"));

        singleChatCompletionsRequest();
    }

    @Test
    @Order(5)
    void decisionsServer200ButAPIError() throws URISyntaxException {
        // a 200 response carrying an error payload: the neutral error reply always comes with an error status
        mock.raw().on("POST", "/v1/decisions").replies(RawResponse.of(200,
                Map.of("Content-Type", "application/json"),
                """
                        {
                            "error": {
                                "message": "The server had an error while processing your request. Sorry about that!",
                                "type": "server_error",
                                "param": null,
                                "code": null
                            }
                        }
                        """));

        OpenAiRestApi restApi = createClient();

        DecisionCreateRequest request = new DecisionCreateRequest();
        request.model = "gpt-6-luna";
        request.input = "test";
        assertThatThrownBy(() -> restApi.blockingDecision(request,
                OpenAiRestApi.ApiMetadata.builder().openAiApiKey(TOKEN).build()))
                .isInstanceOf(OpenAiApiException.class);

        assertThat(mock.requests()).hasSize(1);
        CapturedRequest captured = mock.requests().get(0);
        assertThat(captured.path()).isEqualTo("/v1/decisions");
        assertThat(captured.query()).isNull();
        assertThat(captured.header("Authorization")).isEqualTo("Bearer " + TOKEN);
    }

    private CapturedRequest singleChatCompletionsRequest() {
        assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.requests().get(0);
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.query()).isNull();
        assertThat(request.header("Authorization")).isEqualTo("Bearer " + TOKEN);
        return request;
    }

    private OpenAiRestApi createClient() throws URISyntaxException {
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(new URI(baseUrl))
                .build(OpenAiRestApi.class);
    }
}
