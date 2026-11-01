package io.quarkiverse.langchain4j.azure.openai.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AzureOpenAiV1EndpointTest extends OpenAiBaseTest {

    private static final String DEPLOYMENT = "my-deployment";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.endpoint-style", "v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.deployment-name", DEPLOYMENT)
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.endpoint", WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel chatModel;

    @Inject
    EmbeddingModel embeddingModel;

    private StubMapping embeddingsStub;

    @BeforeEach
    void setUp() {
        resetRequests();
    }

    @AfterEach
    void removeEmbeddingsStub() {
        if (embeddingsStub != null) {
            wiremock().removeStubMapping(embeddingsStub);
            embeddingsStub = null;
        }
    }

    @Test
    void chatRequestTargetsV1Endpoint() throws IOException {
        ToolSpecification tool = ToolSpecification.builder()
                .name("currentTime")
                .description("Returns the current time")
                .build();

        chatModel.chat(ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .toolSpecifications(tool)
                .build());

        LoggedRequest request = singleLoggedRequest();
        assertThat(request.getUrl()).isEqualTo("/v1/chat/completions");
        assertThat(request.getQueryParams()).doesNotContainKey("api-version");

        Map<String, Object> body = getRequestAsMap();
        assertThat(body).containsEntry("model", DEPLOYMENT);
        assertThat(body).containsKey("tools").doesNotContainKey("functions");
    }

    @Test
    void embeddingRequestTargetsV1Endpoint() throws IOException {
        MappingBuilder embeddingsMapping = post(urlPathEqualTo("/v1/embeddings"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "object": "list",
                                  "data": [{ "object": "embedding", "index": 0, "embedding": [0.1, 0.2] }],
                                  "model": "my-deployment",
                                  "usage": { "prompt_tokens": 1, "total_tokens": 1 }
                                }
                                """));
        embeddingsStub = wiremock().register(embeddingsMapping);

        assertThat(embeddingModel.embed("hello").content().vector()).containsExactly(0.1f, 0.2f);

        LoggedRequest request = singleLoggedRequest();
        assertThat(request.getUrl()).isEqualTo("/v1/embeddings");
        assertThat(request.getQueryParams()).doesNotContainKey("api-version");
        assertThat(getRequestAsMap()).containsEntry("model", DEPLOYMENT)
                .containsEntry("input", List.of("hello"));
    }
}
