package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_CHAT_STREAMING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkus.test.QuarkusUnitTest;

public class ChatStreamingCancellationTest extends WireMockAbstract {

    private static final int EVENTS = 6;
    private static final String RESPONSE_STREAMING_TOKENS = """
            id: 1
            event: message
            data: {"id":"chatcmpl-1","object":"chat.completion.chunk","model_id":"my_super_model","model":"my_super_model","choices":[{"index":0,"finish_reason":null,"delta":{"role":"assistant","content":"one"}}],"created":1749736055}

            id: 2
            event: message
            data: {"id":"chatcmpl-1","object":"chat.completion.chunk","model_id":"my_super_model","model":"my_super_model","choices":[{"index":0,"finish_reason":null,"delta":{"content":"two"}}],"created":1749736055}

            id: 3
            event: message
            data: {"id":"chatcmpl-1","object":"chat.completion.chunk","model_id":"my_super_model","model":"my_super_model","choices":[{"index":0,"finish_reason":null,"delta":{"content":"three"}}],"created":1749736055}

            id: 4
            event: message
            data: {"id":"chatcmpl-1","object":"chat.completion.chunk","model_id":"my_super_model","model":"my_super_model","choices":[{"index":0,"finish_reason":null,"delta":{"content":"four"}}],"created":1749736055}

            id: 5
            event: message
            data: {"id":"chatcmpl-1","object":"chat.completion.chunk","model_id":"my_super_model","model":"my_super_model","choices":[{"index":0,"finish_reason":"stop","delta":{"content":""}}],"created":1749736055}

            id: 6
            event: message
            data: {"id":"chatcmpl-1","object":"chat.completion.chunk","model_id":"my_super_model","model":"my_super_model","choices":[],"created":1749736055,"usage":{"completion_tokens":4,"prompt_tokens":10,"total_tokens":14}}

            """;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .response("my_super_token", new Date())
                .build();
    }

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void cancel_stops_the_stream() {

        var streamDuration = Duration.ofSeconds(3);

        mockWatsonxBuilder(URL_WATSONX_CHAT_STREAMING_API, 200)
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(RESPONSE_STREAMING_TOKENS)
                .chunkedDribbleDelay(EVENTS, streamDuration)
                .build();

        var partialResponses = new CopyOnWriteArrayList<String>();
        var cancelled = new AtomicBoolean(false);
        var completeResponse = new AtomicReference<ChatResponse>();
        var error = new AtomicReference<Throwable>();

        streamingChatModel.chat(List.of(UserMessage.from("UserMessage")), new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                partialResponses.add(partialResponse.text());
                context.streamingHandle().cancel();
                cancelled.set(context.streamingHandle().isCancelled());
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                completeResponse.set(chatResponse);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }
        });

        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> !partialResponses.isEmpty());

        assertThat(cancelled).isTrue();

        await().pollDelay(streamDuration.plusSeconds(1))
                .atMost(Duration.ofMinutes(1))
                .until(() -> true);

        assertThat(partialResponses).hasSize(1);
        assertThat(completeResponse.get()).isNull();
        assertThat(error.get()).isNull();
    }
}
