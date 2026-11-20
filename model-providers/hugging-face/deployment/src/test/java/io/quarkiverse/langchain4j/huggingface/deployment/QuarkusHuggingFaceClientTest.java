package io.quarkiverse.langchain4j.huggingface.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.List;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.huggingface.client.EmbeddingRequest;
import dev.langchain4j.model.huggingface.client.Options;
import dev.langchain4j.model.huggingface.client.Parameters;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import io.quarkiverse.langchain4j.huggingface.HuggingFaceRestApi;
import io.quarkiverse.langchain4j.huggingface.QuarkusHuggingFaceClientFactory;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.QuarkusExtensionTest;

public class QuarkusHuggingFaceClientTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");
    private static final String API_KEY = "key";

    @Inject
    MockModelServer mock;

    @ConfigProperty(name = "quarkus.langchain4j.huggingface.chat-model.inference-endpoint-url")
    URL chatEndpointUrl;

    @ConfigProperty(name = "quarkus.langchain4j.huggingface.embedding-model.inference-endpoint-url")
    URL embeddingEndpointUrl;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void chat() {
        mock.chat().reply(ChatReply.text("\nI'm hanging with my bro...\nAnd so on and so forth...\nSo,"));

        TextGenerationRequest request = TextGenerationRequest.builder()
                .inputs("What are you doing?")
                .parameters(Parameters.builder()
                        .temperature(1.0)
                        .returnFullText(false)
                        .build())
                .options(Options.builder()
                        .waitForModel(true)
                        .build())
                .build();
        TextGenerationResponse response = createClientForChat().chat(request);
        assertThat(response).isNotNull().satisfies(r -> {
            assertThat(r.getGeneratedText()).contains("hanging with");
        });

        CapturedRequest captured = mock.chat().singleRequest();
        assertThat(captured.path()).isEqualTo("/huggingface/chat");
        assertThat(captured.query()).isNull();
        assertThat(captured.header("Authorization")).isEqualTo("Bearer " + API_KEY);
    }

    @Test
    void embed() {
        mock.embeddings().reply(EmbeddingReply.of(
                new float[] { -0.06277172267436981f, 0.054958775639534f, 0.052164800465106964f, 0.08578997850418091f }));

        List<float[]> response = createClient().embed(new EmbeddingRequest(List.of("whatever"), true));
        assertThat(response).singleElement().satisfies(fa -> {
            assertThat(fa).hasSize(4);
        });

        CapturedRequest captured = mock.embeddings().singleRequest();
        assertThat(captured.path()).isEqualTo("/huggingface/embeddings");
        assertThat(captured.query()).isNull();
        assertThat(captured.header("Authorization")).isEqualTo("Bearer " + API_KEY);
    }

    private QuarkusHuggingFaceClientFactory.QuarkusHuggingFaceClient createClient() {
        HuggingFaceRestApi restApi = QuarkusRestClientBuilder.newBuilder()
                .baseUrl(embeddingEndpointUrl)
                .build(HuggingFaceRestApi.class);
        return new QuarkusHuggingFaceClientFactory.QuarkusHuggingFaceClient(restApi, API_KEY);
    }

    private QuarkusHuggingFaceClientFactory.QuarkusHuggingFaceClient createClientForChat() {
        HuggingFaceRestApi restApi = RestClientBuilder.newBuilder()
                .baseUrl(chatEndpointUrl)
                .build(HuggingFaceRestApi.class);
        return new QuarkusHuggingFaceClientFactory.QuarkusHuggingFaceClient(restApi, API_KEY);
    }
}
