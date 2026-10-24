package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_EMBEDDING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_SCORING_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.ErrorReply;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkus.test.QuarkusExtensionTest;

public class RetryInterceptorTest extends WatsonxMockTestBase {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse.langchain4j.watsonx.runtime.client\".level", "DEBUG")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WatsonxTestUtil.class));

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    ScoringModel scoringModel;

    @Test
    void test_on_retryable_status_code_retry_for_chat_model() {

        mock.chat().replies(retryableErrorsThen(ChatReply.text("AI Response").usage(59, 47)));

        assertDoesNotThrow(() -> chatModel.chat("Hello"));
        assertRetried(mock.chat().requests(), URL_WATSONX_CHAT_API);
    }

    @Test
    void test_on_retryable_status_code_retry_for_streaming_chat_model() throws InterruptedException {

        mock.chat().replies(retryableErrorsThen(ChatReply.text("Hello").usage(38, 3)));

        CountDownLatch latch = new CountDownLatch(1);
        assertDoesNotThrow(() -> streamingChatModel.chat("Hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
            }
        }));

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertRetried(mock.chat().requests(), URL_WATSONX_CHAT_STREAMING_API);
    }

    @Test
    void test_on_retryable_status_code_retry_for_embedding_model() {

        mock.embeddings().replies(retryableErrorsThen(
                EmbeddingReply.of(new float[] { -0.006929283f, -0.005336422f, -0.024047505f })));

        assertDoesNotThrow(() -> embeddingModel.embed("Hello"));
        assertRetried(mock.embeddings().requests(), URL_WATSONX_EMBEDDING_API);
    }

    @Test
    void test_on_retryable_status_code_retry_for_score_model() {

        mock.scoring().replies(retryableErrorsThen(ScoringReply.of(-2.5847978591918945)));

        assertDoesNotThrow(() -> scoringModel.score("Hello", "Hello"));
        assertRetried(mock.scoring().requests(), URL_WATSONX_SCORING_API);
    }

    /**
     * One reply for each status code the client retries, then the given reply.
     */
    private static List<Reply> retryableErrorsThen(Reply reply) {
        return List.of(ErrorReply.of(429, "Too Many Requests"), ErrorReply.of(503, "Service Unavailable"),
                ErrorReply.of(504, "Gateway Timeout"), ErrorReply.of(520, "Unknown Error"), reply);
    }

    private static void assertRetried(List<CapturedRequest> requests, String path) {
        assertThat(requests).hasSize(5).allSatisfy(request -> assertWatsonxRequest(request, path));
    }
}
