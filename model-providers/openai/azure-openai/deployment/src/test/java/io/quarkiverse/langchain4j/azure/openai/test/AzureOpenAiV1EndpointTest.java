package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.databind.JsonNode;

public class AzureOpenAiV1EndpointTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "azure-openai")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "azure-openai")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.endpoint-style", "v1");

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
    void chatRequestUsesV1Style() {
        mock.chat().reply("ok");
        ToolSpecification tool = ToolSpecification.builder()
                .name("currentTime")
                .description("Returns the current time")
                .build();

        chatModel.chat(ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .toolSpecifications(tool)
                .build());

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).endsWith("/chat/completions");
        assertThat(request.query()).isNull();
        JsonNode body = request.bodyAsJson();
        assertThat(body.path("model").asText()).isEqualTo("mock-deployment");
        assertThat(body.has("tools")).isTrue();
        assertThat(body.has("functions")).isFalse();
    }

    @Test
    void embeddingRequestUsesV1Style() {
        mock.embeddings().reply(EmbeddingReply.of(new float[] { 0.1f, 0.2f }));

        assertThat(embeddingModel.embed("hello").content().vector()).containsExactly(0.1f, 0.2f);

        CapturedRequest request = mock.embeddings().singleRequest();
        assertThat(request.path()).endsWith("/embeddings");
        assertThat(request.query()).isNull();
        JsonNode body = request.bodyAsJson();
        assertThat(body.path("model").asText()).isEqualTo("mock-deployment");
        assertThat(body.path("input")).hasSize(1);
        assertThat(body.path("input").get(0).asText()).isEqualTo("hello");
    }
}
