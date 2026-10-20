package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The deployment and model gateway chat backends, each selected by its own config group of a named model.
 */
public class WatsonxDeploymentAndGatewayTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.watsonx.dep.deployment-chat-model.deployment-id=my-deployment
                            quarkus.langchain4j.watsonx.gw.gateway-chat-model.model-name=openai/gpt-4o
                            quarkus.langchain4j.watsonx.gw.gateway-embedding-model.model-name=openai/text-embedding-3-small
                            """), "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.dep.chat-model.provider", "watsonx")
            .overrideConfigKey("quarkus.langchain4j.gw.chat-model.provider", "watsonx")
            .overrideConfigKey("quarkus.langchain4j.gw.embedding-model.provider", "watsonx");

    @Inject
    @ModelName("dep")
    ChatModel deploymentChat;

    @Inject
    @ModelName("dep")
    StreamingChatModel deploymentStreamingChat;

    @Inject
    @ModelName("gw")
    ChatModel gatewayChat;

    @Inject
    @ModelName("gw")
    StreamingChatModel gatewayStreamingChat;

    @Inject
    @ModelName("gw")
    EmbeddingModel gatewayEmbedding;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void deploymentChat() {
        mock.chat().reply(ChatReply.text("deployed").usage(1, 2));

        ChatResponse response = deploymentChat.chat(ChatRequest.builder().messages(UserMessage.from("hi")).build());

        assertThat(response.aiMessage().text()).isEqualTo("deployed");
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(3);
        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/watsonx/ml/v1/deployments/my-deployment/text/chat");
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-iam-token");
        assertThat(r.asChat().messages()).singleElement().satisfies(m -> assertThat(m.text()).isEqualTo("hi"));
    }

    @Test
    void deploymentStreamingChat() throws Exception {
        mock.chat().reply("streamed from a deployment");

        ChatResponse response = stream(deploymentStreamingChat);

        assertThat(response.aiMessage().text()).isEqualTo("streamed from a deployment");
        assertThat(mock.chat().singleRequest().path())
                .isEqualTo("/watsonx/ml/v1/deployments/my-deployment/text/chat_stream");
    }

    @Test
    void gatewayChat() {
        mock.chat().reply(ChatReply.text("through the gateway").usage(2, 3));

        assertThat(gatewayChat.chat("hi")).isEqualTo("through the gateway");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/watsonx/ml/gateway/v1/chat/completions");
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-iam-token");
        assertThat(r.asChat().modelName()).isEqualTo("openai/gpt-4o");
        assertThat(r.asChat().stream()).isFalse();
    }

    @Test
    void gatewayStreamingChat() throws Exception {
        mock.chat().reply(ChatReply.text("gateway stream").usage(2, 3));

        ChatResponse response = stream(gatewayStreamingChat);

        assertThat(response.aiMessage().text()).isEqualTo("gateway stream");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(mock.chat().singleRequest().asChat().stream()).isTrue();
    }

    @Test
    void gatewayEmbeddings() {
        mock.embeddings().reply(EmbeddingReply.of(new float[] { 0.5f, 0.6f }));

        Response<List<Embedding>> response = gatewayEmbedding.embedAll(List.of(TextSegment.from("a")));

        assertThat(response.content()).singleElement()
                .satisfies(e -> assertThat(e.vector()).containsExactly(0.5f, 0.6f));
        CapturedRequest r = mock.embeddings().singleRequest();
        assertThat(r.path()).isEqualTo("/watsonx/ml/gateway/v1/embeddings");
        assertThat(r.bodyAsJson().at("/model").asText()).isEqualTo("openai/text-embedding-3-small");
    }

    private static ChatResponse stream(StreamingChatModel model) throws Exception {
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        model.chat("hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
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
        return done.get(10, TimeUnit.SECONDS);
    }
}
