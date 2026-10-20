package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_STREAMING_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

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
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkus.test.QuarkusExtensionTest;

public class ChatStreamingCancellationTest extends WatsonxMockTestBase {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WatsonxTestUtil.class));

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void cancel_stops_the_stream() {

        // the mock sends the whole stream at once: what is left of it must not reach the handler after the cancel
        var streamDuration = Duration.ofSeconds(3);

        mock.chat().reply(ChatReply.text("onetwothreefour").chunks(List.of("one", "two", "three", "four")).usage(10, 4));

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
        assertWatsonxRequest(mock.chat().singleRequest(), URL_WATSONX_CHAT_STREAMING_API);
    }
}
