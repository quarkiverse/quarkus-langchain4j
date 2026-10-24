package io.quarkiverse.langchain4j.bedrock.deployment;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Neither a region nor a credentials provider is configured, and the build hides the AWS default chain (environment,
 * profile files, instance metadata): the mock's region and credentials bean let the models start and sign their
 * requests.
 */
public class BedrockIdentityTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "bedrock")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "bedrock")
            .overrideConfigKey("quarkus.langchain4j.bedrock.embedding-model.cohere.input-type", "search_query");

    @Inject
    ChatModel chatModel;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    MockModelServer mock;

    @Test
    void requestsAreSignedWithTheMockCredentialsAndRegion() {
        mock.reset();

        chatModel.chat("hi");
        embeddingModel.embed("text");

        assertThat(mock.chat().singleRequest().header("Authorization")).satisfies(this::signedByTheMock);
        CapturedRequest embeddings = mock.embeddings().singleRequest();
        assertThat(embeddings.header("Authorization")).satisfies(this::signedByTheMock);
    }

    private void signedByTheMock(String authorization) {
        assertThat(authorization).startsWith("AWS4-HMAC-SHA256 ")
                .contains("Credential=mock-access-key/")
                .contains("/us-east-1/bedrock/aws4_request");
    }
}
