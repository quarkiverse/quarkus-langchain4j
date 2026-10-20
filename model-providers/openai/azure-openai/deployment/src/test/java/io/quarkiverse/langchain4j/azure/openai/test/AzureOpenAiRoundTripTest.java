package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class AzureOpenAiRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "azure-openai")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "azure-openai");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    MockModelServer mock;

    @Test
    void chatReachesTheMockThroughTheAzureClient() {
        mock.reset();
        mock.chat().reply("Hello from Azure");

        assertThat(chatModel.chat("hi")).isEqualTo("Hello from Azure");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).contains("/openai/deployments/mock-deployment").endsWith("/chat/completions");
        assertThat(r.query()).contains("api-version");
        assertThat(r.header("api-key")).isEqualTo("mock-api-key");
    }

    @Test
    void streamingChatReachesTheMockThroughTheAzureClient() throws Exception {
        mock.reset();
        mock.chat().reply("Streamed from Azure");

        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        AtomicInteger partials = new AtomicInteger();
        streamingChatModel.chat("hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partials.incrementAndGet();
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                done.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        });

        assertThat(done.get(10, TimeUnit.SECONDS).aiMessage().text()).isEqualTo("Streamed from Azure");
        assertThat(partials.get()).isGreaterThanOrEqualTo(1);
        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).contains("/openai/deployments/mock-deployment").endsWith("/chat/completions");
        assertThat(r.asChat().stream()).isTrue();
    }

    @Test
    void embeddingsReachTheMockThroughTheAzureClient() {
        mock.reset();
        mock.embeddings().reply(EmbeddingReply.of(new float[] { 1, 0, 0, 0 }, new float[] { 0, 1, 0, 0 }));

        List<Embedding> embeddings = embeddingModel.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b")))
                .content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(1).vector()).containsExactly(0, 1, 0, 0);
        CapturedRequest r = mock.embeddings().singleRequest();
        assertThat(r.path()).contains("/openai/deployments/mock-deployment").endsWith("/embeddings");
        assertThat(r.header("api-key")).isEqualTo("mock-api-key");
    }
}
