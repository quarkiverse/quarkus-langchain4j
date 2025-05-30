package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.streamingChatResponseHandler;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkus.test.QuarkusUnitTest;

public class AiChatCacheTokenTest extends WireMockAbstract {

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
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Override
    void handlerBeforeEach() throws Exception {
        Thread.sleep(cacheTimeout);
    }

    @Test
    void try_token_cache() throws InterruptedException {

        Date date = Date.from(Instant.now().plusMillis(cacheTimeout));

        // First call returns 200.
        mockIAMBuilder(200)
                .scenario(Scenario.STARTED, "error")
                .response("3secondstoken", date)
                .build();

        // All other call after 2 seconds they will give an error.
        mockIAMBuilder(401)
                .scenario("error", Scenario.STARTED)
                .response("Should never happen")
                .build();

        Stream.of(
                Map.entry(URL_WATSONX_CHAT_API, RESPONSE_WATSONX_CHAT_API),
                Map.entry(URL_WATSONX_CHAT_STREAMING_API, RESPONSE_WATSONX_CHAT_STREAMING_API))
                .forEach(entry -> {

                    WatsonxBuilder builder = mockWatsonxBuilder(entry.getKey(), 200);

                    builder.token("3secondstoken")
                            .responseMediaType(entry.getKey().equals(URL_WATSONX_CHAT_STREAMING_API)
                                    ? MediaType.SERVER_SENT_EVENTS
                                    : MediaType.APPLICATION_JSON)
                            .response(entry.getValue())
                            .build();

                    switch (entry.getKey()) {
                        case URL_WATSONX_CHAT_API -> {
                            assertDoesNotThrow(() -> chatModel.chat("message"));
                            assertDoesNotThrow(() -> chatModel.chat("message")); // cache
                        }
                        case URL_WATSONX_CHAT_STREAMING_API ->
                            assertDoesNotThrow(() -> chatModel.chat("message")); // cache.
                    }
                });
    }

    @Test
    void try_token_retry() throws InterruptedException {

        // Return an expired token.
        mockIAMBuilder(200)
                .scenario(Scenario.STARTED, "retry")
                .response("expired_token", Date.from(Instant.now().minusSeconds(3)))
                .build();

        // Second call (retryOn) returns 200
        mockIAMBuilder(200)
                .scenario("retry", Scenario.STARTED)
                .response("my_super_token", Date.from(Instant.now().plusMillis(cacheTimeout)))
                .build();

        Stream.of(
                Map.entry(URL_WATSONX_CHAT_API, RESPONSE_WATSONX_CHAT_API),
                Map.entry(URL_WATSONX_CHAT_STREAMING_API, RESPONSE_WATSONX_CHAT_STREAMING_API))
                .forEach(entry -> {
                    mockWatsonxBuilder(entry.getKey(), 401)
                            .token("expired_token")
                            .scenario(Scenario.STARTED, "retry")
                            .response(RESPONSE_401)
                            .build();

                    mockWatsonxBuilder(entry.getKey(), 200)
                            .token("my_super_token")
                            .scenario("retry", Scenario.STARTED)
                            .responseMediaType(entry.getKey().equals(URL_WATSONX_CHAT_STREAMING_API)
                                    ? MediaType.SERVER_SENT_EVENTS
                                    : MediaType.APPLICATION_JSON)
                            .response(entry.getValue())
                            .build();

                    switch (entry.getKey()) {
                        case URL_WATSONX_CHAT_API -> assertDoesNotThrow(() -> chatModel.chat("message"));
                        case URL_WATSONX_CHAT_STREAMING_API -> {
                            var streamingResponse = new AtomicReference<ChatResponse>();
                            assertDoesNotThrow(() -> streamingChatModel.chat("message",
                                    streamingChatResponseHandler(streamingResponse)));
                            await().atMost(Duration.ofSeconds(6))
                                    .pollInterval(Duration.ofSeconds(2))
                                    .until(() -> streamingResponse.get() != null);
                            assertNotNull(streamingResponse.get());
                        }
                    }
                    try {
                        Thread.sleep(cacheTimeout);
                    } catch (InterruptedException e) {
                        fail(e);
                    }
                });
    }
}
