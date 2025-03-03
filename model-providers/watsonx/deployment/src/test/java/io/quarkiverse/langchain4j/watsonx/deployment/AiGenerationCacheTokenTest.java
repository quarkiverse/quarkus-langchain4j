package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.streamingChatResponseHandler;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkus.test.QuarkusUnitTest;

public class AiGenerationCacheTokenTest extends WireMockAbstract {

    static int cacheTimeout = 2000;
    static String RESPONSE_401 = """
            {
                "errors": [
                    {
                        "code": "authentication_token_expired",
                        "message": "Failed to authenticate the request due to an expired token",
                        "more_info": "https://cloud.ibm.com/apidocs/watsonx-ai"
                    }
                ],
                "trace": "fc4afd38813180730e10a5a3d56c1f25",
                "status_code": 401
            }
            """;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.mode", "generation")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() throws Exception {
        Thread.sleep(cacheTimeout);
    }

    @Inject
    ChatLanguageModel chatModel;

    @Inject
    StreamingChatLanguageModel streamingChatModel;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    TokenCountEstimator tokenCountEstimator;

    @Test
    void try_token_cache() throws InterruptedException {

        Date date = Date.from(Instant.now().plusMillis(cacheTimeout));

        // First call returns 200.
        mockServers.mockIAMBuilder(200)
                .scenario(Scenario.STARTED, "error")
                .response("3secondstoken", date)
                .build();

        // All other call after 2 seconds they will give an error.
        mockServers.mockIAMBuilder(401)
                .scenario("error", Scenario.STARTED)
                .response("Should never happen")
                .build();

        Stream.of(
                Map.entry(WireMockUtil.URL_WATSONX_GENERATION_API, WireMockUtil.RESPONSE_WATSONX_GENERATION_API),
                Map.entry(WireMockUtil.URL_WATSONX_EMBEDDING_API, WireMockUtil.RESPONSE_WATSONX_EMBEDDING_API),
                Map.entry(WireMockUtil.URL_WATSONX_GENERATION_STREAMING_API,
                        WireMockUtil.RESPONSE_WATSONX_GENERATION_STREAMING_API),
                Map.entry(WireMockUtil.URL_WATSONX_TOKENIZER_API, WireMockUtil.RESPONSE_WATSONX_TOKENIZER_API))
                .forEach(entry -> {
                    mockServers.mockWatsonxBuilder(entry.getKey(), 200)
                            .token("3secondstoken")
                            .responseMediaType(entry.getKey().equals(WireMockUtil.URL_WATSONX_GENERATION_STREAMING_API)
                                    ? MediaType.SERVER_SENT_EVENTS
                                    : MediaType.APPLICATION_JSON)
                            .response(entry.getValue())
                            .build();
                });

        // --- Test ChatLanguageModel --- //
        assertDoesNotThrow(() -> chatModel.chat("message"));
        assertDoesNotThrow(() -> chatModel.chat("message")); // cache.

        // --- Test EmbeddingModel --- //
        assertDoesNotThrow(() -> embeddingModel.embed("message")); // cache.

        // --- Test TokenCountEstimator --- //
        assertDoesNotThrow(() -> tokenCountEstimator.estimateTokenCount("message"));

        // --- Test StreamingChatLanguageModel --- //
        streamingChatModel.chat("message", streamingChatResponseHandler(new AtomicReference<ChatResponse>())); // cache.

        Thread.sleep(cacheTimeout);
    }

    @Test
    void try_token_retry() throws InterruptedException {

        // Return an expired token.
        mockServers.mockIAMBuilder(200)
                .scenario(Scenario.STARTED, "retry")
                .response("expired_token", Date.from(Instant.now().minusSeconds(3)))
                .build();

        // Second call (retryOn) returns 200
        mockServers.mockIAMBuilder(200)
                .scenario("retry", Scenario.STARTED)
                .response("my_super_token", Date.from(Instant.now().plusMillis(cacheTimeout)))
                .build();

        Stream.of(
                Map.entry(WireMockUtil.URL_WATSONX_GENERATION_API, WireMockUtil.RESPONSE_WATSONX_GENERATION_API),
                Map.entry(WireMockUtil.URL_WATSONX_EMBEDDING_API, WireMockUtil.RESPONSE_WATSONX_EMBEDDING_API),
                Map.entry(WireMockUtil.URL_WATSONX_GENERATION_STREAMING_API,
                        WireMockUtil.RESPONSE_WATSONX_GENERATION_STREAMING_API),
                Map.entry(WireMockUtil.URL_WATSONX_TOKENIZER_API, WireMockUtil.RESPONSE_WATSONX_TOKENIZER_API))
                .forEach(entry -> {
                    mockServers.mockWatsonxBuilder(entry.getKey(), 401)
                            .token("expired_token")
                            .scenario(Scenario.STARTED, "retry")
                            .response(RESPONSE_401)
                            .build();

                    mockServers.mockWatsonxBuilder(entry.getKey(), 200)
                            .token("my_super_token")
                            .scenario("retry", Scenario.STARTED)
                            .responseMediaType(entry.getKey().equals(WireMockUtil.URL_WATSONX_GENERATION_STREAMING_API)
                                    ? MediaType.SERVER_SENT_EVENTS
                                    : MediaType.APPLICATION_JSON)
                            .response(entry.getValue())
                            .build();
                });

        // --- Test ChatLanguageModel --- //
        assertDoesNotThrow(() -> chatModel.chat("message"));

        Thread.sleep(cacheTimeout);

        // --- Test EmbeddingModel --- //
        assertDoesNotThrow(() -> embeddingModel.embed("message"));

        Thread.sleep(cacheTimeout);

        // --- Test TokenCountEstimator --- //
        assertDoesNotThrow(() -> tokenCountEstimator.estimateTokenCount("message"));

        // --- Test StreamingChatLanguageModel --- //
        var streamingResponse = new AtomicReference<ChatResponse>();
        streamingChatModel.chat("message", streamingChatResponseHandler(streamingResponse));
        await().atMost(Duration.ofSeconds(6))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);
        assertNotNull(streamingResponse.get());
    }
}
