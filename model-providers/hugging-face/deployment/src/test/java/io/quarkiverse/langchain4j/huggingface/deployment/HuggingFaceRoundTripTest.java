package io.quarkiverse.langchain4j.huggingface.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class HuggingFaceRoundTripTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "huggingface")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "huggingface");

    @Inject
    ChatModel chatModel;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void textReplyWithPlaceholderApiKey() {
        mock.chat().reply("Hello from the mock");

        assertThat(chatModel.chat("hi")).isEqualTo("Hello from the mock");

        CapturedRequest r = mock.chat().singleRequest();
        assertThat(r.path()).isEqualTo("/huggingface/chat");
        assertThat(r.dialect()).isEqualTo("huggingface");
        // no api-key is configured: the extension fills in the placeholder
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-api-key");
        assertThat(r.bodyAsJson().at("/inputs").asText()).isEqualTo("hi");
        assertThat(r.bodyAsJson().at("/parameters/return_full_text").asBoolean(true)).isFalse();
        assertThat(r.asChat().temperature()).isEqualTo(1.0);
        assertThat(r.asChat().messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("hi");
        });
    }

    @Test
    void embedAllReturnsOneVectorPerInput() {
        mock.embeddings().reply(EmbeddingReply.of(new float[] { 1, 0, 0 }, new float[] { 0, 1, 0 }));

        List<Embedding> embeddings = embeddingModel.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b")))
                .content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).containsExactly(1, 0, 0);
        assertThat(embeddings.get(1).vector()).containsExactly(0, 1, 0);
        CapturedRequest r = mock.embeddings().singleRequest();
        assertThat(r.path()).isEqualTo("/huggingface/embeddings");
        assertThat(r.dialect()).isEqualTo("huggingface");
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-api-key");
        assertThat(r.bodyAsJson().at("/inputs")).hasSize(2);
    }

    @Test
    void errorSurfacesAsException() {
        mock.chat().fail(429, "Model is overloaded");

        assertThatThrownBy(() -> chatModel.chat("hi")).hasMessageContaining("429");
        assertThat(mock.chat().requests()).hasSize(1);
    }
}
