package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_IAM_GENERATE_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_EMBEDDING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_SCORING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.streamingChatResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkus.test.QuarkusExtensionTest;

public class AiChatCacheTokenTest extends WatsonxMockTestBase {

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
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WatsonxTestUtil.class));

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    ScoringModel scoringModel;

    @BeforeEach
    void waitForTheTokenToExpire() throws InterruptedException {
        Thread.sleep(cacheTimeout);
    }

    @Test
    void try_token_cache() throws InterruptedException {

        Date date = Date.from(Instant.now().plusMillis(cacheTimeout));

        // The first call returns 200, all the others an error.
        mock.raw().on("POST", URL_IAM_GENERATE_TOKEN).replies(
                iamToken("3secondstoken", date),
                RawResponse.json(401, "Should never happen"));

        mock.chat().replies(List.of(
                ChatReply.text("AI Response").usage(59, 47),
                ChatReply.text("AI Response").usage(59, 47),
                ChatReply.text("AI Response").usage(59, 47)));
        mock.embeddings().reply(EmbeddingReply.of(new float[] { -0.006929283f, -0.005336422f, -0.024047505f }));
        mock.scoring().reply(ScoringReply.of(-2.5847978591918945));

        assertDoesNotThrow(() -> chatModel.chat("message"));
        assertDoesNotThrow(() -> chatModel.chat("message")); // cache
        assertDoesNotThrow(() -> chatModel.chat("message")); // cache.
        assertDoesNotThrow(() -> embeddingModel.embed("message")); // cache.
        assertDoesNotThrow(() -> scoringModel.score("message", "message")); // cache.

        assertThat(iamRequests()).hasSize(1);
        assertThat(apiRequests()).extracting(CapturedRequest::path).containsExactly(
                URL_WATSONX_CHAT_API, URL_WATSONX_CHAT_API, URL_WATSONX_CHAT_API, URL_WATSONX_EMBEDDING_API,
                URL_WATSONX_SCORING_API);
        assertThat(apiRequests()).allSatisfy(request -> assertToken(request, "3secondstoken"));
    }

    @Test
    void try_token_retry() throws InterruptedException {

        // Every token request first returns an expired token, then (retryOn) a valid one.
        RawResponse expiredToken = iamToken("expired_token", Date.from(Instant.now().minusSeconds(3)));
        RawResponse validToken = iamToken("my_super_token", Date.from(Instant.now().plusMillis(cacheTimeout)));
        mock.raw().on("POST", URL_IAM_GENERATE_TOKEN).replies(
                expiredToken, validToken, expiredToken, validToken, expiredToken, validToken, expiredToken, validToken);

        // The expired token is refused (a watsonx.ai error code the neutral errors never use), the valid one accepted.
        RawResponse tokenExpired = RawResponse.json(401, RESPONSE_401);
        mock.chat().replies(List.of(
                tokenExpired, ChatReply.text("AI Response").usage(59, 47),
                tokenExpired, ChatReply.text("Hello").usage(38, 3)));
        mock.embeddings().replies(List.of(
                tokenExpired, EmbeddingReply.of(new float[] { -0.006929283f, -0.005336422f, -0.024047505f })));
        mock.scoring().replies(List.of(tokenExpired, ScoringReply.of(-2.5847978591918945)));

        assertDoesNotThrow(() -> chatModel.chat("message"));
        Thread.sleep(cacheTimeout);

        var streamingResponse = new AtomicReference<ChatResponse>();
        assertDoesNotThrow(() -> streamingChatModel.chat("message", streamingChatResponseHandler(streamingResponse)));
        await().atMost(Duration.ofSeconds(6))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);
        assertNotNull(streamingResponse.get());
        Thread.sleep(cacheTimeout);

        assertDoesNotThrow(() -> embeddingModel.embed("message"));
        Thread.sleep(cacheTimeout);

        assertDoesNotThrow(() -> scoringModel.score("message", "message"));

        assertThat(iamRequests()).hasSize(8);
        assertThat(mock.chat().requests()).hasSize(4);
        assertRetriedWithNewToken(mock.chat().requests().subList(0, 2), URL_WATSONX_CHAT_API);
        assertRetriedWithNewToken(mock.chat().requests().subList(2, 4), URL_WATSONX_CHAT_STREAMING_API);
        assertRetriedWithNewToken(mock.embeddings().requests(), URL_WATSONX_EMBEDDING_API);
        assertRetriedWithNewToken(mock.scoring().requests(), URL_WATSONX_SCORING_API);
    }

    private static void assertRetriedWithNewToken(List<CapturedRequest> requests, String path) {
        assertThat(requests).hasSize(2).allSatisfy(request -> assertThat(request.path()).isEqualTo(path));
        assertToken(requests.get(0), "expired_token");
        assertToken(requests.get(1), "my_super_token");
    }

    private static void assertToken(CapturedRequest request, String token) {
        assertThat(queryParams(request)).hasEntrySatisfying("version",
                version -> assertThat(version).matches("\\d{4}-\\d{2}-\\d{2}"));
        // any of the Authorization values, as the stubs matched: the retry of a streaming request keeps the expired
        // token's header and adds one with the new token
        assertThat(request.headers().entrySet()).filteredOn(header -> header.getKey().equalsIgnoreCase("Authorization"))
                .flatExtracting(Map.Entry::getValue).contains("Bearer " + token);
    }
}
