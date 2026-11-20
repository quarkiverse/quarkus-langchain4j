package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Neither an api-key nor a project-id is configured: the placeholders make the models start, the IAM token request
 * reaches the mock and every model request carries the mock's token.
 */
public class WatsonxIdentityTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "watsonx")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "watsonx");

    @Inject
    ChatModel chatModel;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    MockModelServer mock;

    @Test
    void iamTokenFromTheMockIsUsedByEveryModel() {
        mock.reset();

        chatModel.chat("hi");
        chatModel.chat("again");
        embeddingModel.embed("text");

        // the provider caches one authenticator per api-key, shared by all its models, and keeps the token until it
        // expires, so a single token request serves both models and every call
        List<CapturedRequest> tokenRequests = mock.requests().stream()
                .filter(r -> r.path().equals("/watsonx/identity/token"))
                .toList();
        assertThat(tokenRequests).singleElement().satisfies(r -> {
            assertThat(r.method()).isEqualTo("POST");
            assertThat(r.dialect()).isEqualTo("watsonx");
            assertThat(r.header("Content-Type")).startsWith("application/x-www-form-urlencoded");
            assertThat(r.body()).contains("apikey=mock-api-key")
                    .contains("grant_type=urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey");
        });

        assertThat(mock.chat().requests()).hasSize(2).allSatisfy(r -> {
            assertThat(r.header("Authorization")).isEqualTo("Bearer mock-iam-token");
            assertThat(r.bodyAsJson().at("/project_id").asText()).isEqualTo("mock-project");
        });
        CapturedRequest embeddings = mock.embeddings().singleRequest();
        assertThat(embeddings.path()).isEqualTo("/watsonx/ml/v1/text/embeddings");
        assertThat(embeddings.header("Authorization")).isEqualTo("Bearer mock-iam-token");
        assertThat(embeddings.bodyAsJson().at("/project_id").asText()).isEqualTo("mock-project");
    }
}
