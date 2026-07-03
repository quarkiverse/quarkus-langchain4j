package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.chat.ChatHandler;
import com.ibm.watsonx.ai.chat.ChatResponse;
import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.PartialChatResponse;
import com.ibm.watsonx.ai.core.auth.ibmcloud.IBMCloudAuthenticator;
import com.ibm.watsonx.ai.core.exception.WatsonxException;

import io.quarkus.test.QuarkusUnitTest;

public class ChatStreamingFutureTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType().orElse(null))
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    private ChatService chatService() {
        var authenticator = IBMCloudAuthenticator.builder()
                .baseUrl(URI.create(URL_IAM_SERVER))
                .apiKey(API_KEY)
                .build();

        return ChatService.builder()
                .authenticator(authenticator)
                .baseUrl(URL_WATSONX_SERVER)
                .version(VERSION)
                .projectId(PROJECT_ID)
                .modelId("my-model")
                .build();
    }

    @Test
    void streaming_future_completes_exceptionally_on_non_retryable_error() throws Exception {

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 400)
                .responseMediaType(MediaType.APPLICATION_JSON)
                .response("""
                        {
                            "errors": [
                                {
                                    "code": "json_validation_error",
                                    "message": "boom"
                                }
                            ],
                            "trace": "xxx",
                            "status_code": 400
                        }
                        """)
                .build();

        var errorRef = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);

        CompletableFuture<ChatResponse> future = chatService().chatStreaming("Hello", new ChatHandler() {
            @Override
            public void onPartialResponse(String partialResponse, PartialChatResponse partialChatResponse) {
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
        assertInstanceOf(WatsonxException.class, ex.getCause());
        assertTrue(latch.await(10, TimeUnit.SECONDS), "handler.onError was never invoked");
        assertNotNull(errorRef.get());
    }

    @Test
    void streaming_future_completes_on_success() throws Exception {

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 200)
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(RESPONSE_WATSONX_CHAT_STREAMING_API)
                .build();

        var partial = new StringBuilder();

        CompletableFuture<ChatResponse> future = chatService().chatStreaming("Hello", new ChatHandler() {
            @Override
            public void onPartialResponse(String partialResponse, PartialChatResponse partialChatResponse) {
                partial.append(partialResponse);
            }
        });

        ChatResponse response = future.get(10, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals("Hello", partial.toString());
    }
}
