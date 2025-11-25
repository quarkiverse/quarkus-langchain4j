package io.quarkiverse.langchain4j.watsonx.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_EMBEDDING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_EMBEDDING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SCORING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkus.test.QuarkusUnitTest;

public class RetryInterceptorTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse.langchain4j.watsonx.runtime.client\".level", "DEBUG")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType().orElse(null))
                .response(BEARER_TOKEN, new Date())
                .build();
    }

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

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 429)
                .scenario(Scenario.STARTED, "to_503")
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 503)
                .scenario("to_503", "to_504")
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 504)
                .scenario("to_504", "to_520")
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 520)
                .scenario("to_520", "to_200")
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_API, 200)
                .scenario("to_200", Scenario.STARTED)
                .response(RESPONSE_WATSONX_CHAT_API)
                .build();

        assertDoesNotThrow(() -> chatModel.chat("Hello"));
        watsonxServer.verify(5, postRequestedFor(urlPathEqualTo("/ml/v1/text/chat")));
    }

    @Test
    void test_on_retryable_status_code_retry_for_streaming_chat_model() throws InterruptedException {

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 429)
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .scenario(Scenario.STARTED, "to_503")
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 503)
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .scenario("to_503", "to_504")
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 504)
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .scenario("to_504", "to_520")
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 520)
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .scenario("to_520", "to_200")
                .build();

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 200)
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .scenario("to_200", Scenario.STARTED)
                .response(RESPONSE_WATSONX_CHAT_STREAMING_API)
                .build();

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
        watsonxServer.verify(5, postRequestedFor(urlPathEqualTo("/ml/v1/text/chat_stream")));
    }

    @Test
    void test_on_retryable_status_code_retry_for_embedding_model() {

        mockWatsonxBuilder(URL_WATSONX_EMBEDDING_API, 429)
                .scenario(Scenario.STARTED, "to_503")
                .build();

        mockWatsonxBuilder(URL_WATSONX_EMBEDDING_API, 503)
                .scenario("to_503", "to_504")
                .build();

        mockWatsonxBuilder(URL_WATSONX_EMBEDDING_API, 504)
                .scenario("to_504", "to_520")
                .build();

        mockWatsonxBuilder(URL_WATSONX_EMBEDDING_API, 520)
                .scenario("to_520", "to_200")
                .build();

        mockWatsonxBuilder(URL_WATSONX_EMBEDDING_API, 200)
                .scenario("to_200", Scenario.STARTED)
                .response(RESPONSE_WATSONX_EMBEDDING_API)
                .build();

        assertDoesNotThrow(() -> embeddingModel.embed("Hello"));
        watsonxServer.verify(5, postRequestedFor(urlPathEqualTo("/ml/v1/text/embeddings")));
    }

    @Test
    void test_on_retryable_status_code_retry_for_score_model() {

        mockWatsonxBuilder(URL_WATSONX_SCORING_API, 429)
                .scenario(Scenario.STARTED, "to_503")
                .build();

        mockWatsonxBuilder(URL_WATSONX_SCORING_API, 503)
                .scenario("to_503", "to_504")
                .build();

        mockWatsonxBuilder(URL_WATSONX_SCORING_API, 504)
                .scenario("to_504", "to_520")
                .build();

        mockWatsonxBuilder(URL_WATSONX_SCORING_API, 520)
                .scenario("to_520", "to_200")
                .build();

        mockWatsonxBuilder(URL_WATSONX_SCORING_API, 200)
                .scenario("to_200", Scenario.STARTED)
                .response("""
                        {
                            "model_id": "cross-encoder/ms-marco-minilm-l-12-v2",
                            "created_at": "2024-10-18T06:57:42.032Z",
                            "results": [
                                {
                                    "index": 0,
                                    "score": -2.5847978591918945
                                }
                            ],
                            "input_token_count": 318
                        }""")
                .build();

        assertDoesNotThrow(() -> scoringModel.score("Hello", "Hello"));
        watsonxServer.verify(5, postRequestedFor(urlPathEqualTo("/ml/v1/text/rerank")));
    }
}
