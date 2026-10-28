package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.DEFAULT_EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_EMBEDDING_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.embedding.EmbeddingPayload;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkus.test.QuarkusExtensionTest;

public class AiEmbeddingTest extends WatsonxMockTestBase {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WatsonxTestUtil.class));

    @Inject
    ChatModel model;

    @Inject
    EmbeddingModel embeddingModel;

    @Test
    void test_embed_text() throws Exception {
        var input = "Embedding THIS!";
        var vector = mockEmbeddingServer(input);

        Response<Embedding> response = embeddingModel.embed(input);
        assertNotNull(response);
        assertNotNull(response.content());
        assertEquals(vector.size(), response.content().vectorAsList().size());
        assertEquals(vector, response.content().vectorAsList());
        assertEmbeddingRequest(List.of(input));
    }

    @Test
    void test_embed_textsegment() throws Exception {
        var input = "Embedding THIS!";
        var vector = mockEmbeddingServer(input);

        Response<Embedding> response = embeddingModel.embed(TextSegment.textSegment(input));
        assertNotNull(response);
        assertNotNull(response.content());
        assertEquals(vector.size(), response.content().vectorAsList().size());
        assertEquals(vector, response.content().vectorAsList());
        assertEmbeddingRequest(List.of(input));
    }

    @Test
    void test_embed_list_of_one_textsegment() throws Exception {
        var input = "Embedding THIS!";
        var vector = mockEmbeddingServer(input);

        Response<List<Embedding>> response = embeddingModel.embedAll(List.of(TextSegment.textSegment(input)));
        assertNotNull(response);
        assertNotNull(response.content());
        assertEquals(1, response.content().size());
        assertEquals(vector.size(), response.content().get(0).vectorAsList().size());
        assertEquals(vector, response.content().get(0).vectorAsList());
        assertEmbeddingRequest(List.of(input));
    }

    @Test
    void test_embed_list_of_three_textsegment() throws Exception {

        var input = "Embedding THIS!";

        float[] embedding = { -0.006929283f, -0.005336422f, -0.024047505f };
        mock.embeddings().reply(EmbeddingReply.of(embedding, embedding, embedding));

        var vector = List.of(-0.006929283f, -0.005336422f, -0.024047505f);

        Response<List<Embedding>> response = embeddingModel.embedAll(
                List.of(TextSegment.textSegment(input), TextSegment.textSegment(input), TextSegment.textSegment(input)));
        assertNotNull(response);
        assertNotNull(response.content());
        assertEquals(3, response.content().size());
        assertEquals(3, response.content().get(0).vectorAsList().size());
        assertEquals(3, response.content().get(1).vectorAsList().size());
        assertEquals(3, response.content().get(2).vectorAsList().size());
        assertEquals(vector, response.content().get(0).vectorAsList());
        assertEquals(vector, response.content().get(1).vectorAsList());
        assertEquals(vector, response.content().get(2).vectorAsList());
        assertEmbeddingRequest(List.of(input, input, input));
    }

    @Test
    public void test_high_embedding_text_segments() throws Exception {
        var list = IntStream.rangeClosed(1, 2001).mapToObj(String::valueOf).collect(Collectors.toList());

        // the mock answers every batch with one embedding per input
        mock.embeddings().defaultReply(EmbeddingReply.derived(3));

        embeddingModel.embedAll(list.stream().map(TextSegment::textSegment).toList());

        // the inputs are sent in batches of 1000
        List<CapturedRequest> requests = mock.embeddings().requests();
        assertThat(requests).hasSize(3);
        assertEmbeddingRequest(requests.get(0), list.subList(0, 1000));
        assertEmbeddingRequest(requests.get(1), list.subList(1000, 2000));
        assertEmbeddingRequest(requests.get(2), list.subList(2000, 2001));
    }

    private List<Float> mockEmbeddingServer(String input) throws Exception {
        mock.embeddings().reply(EmbeddingReply.of(new float[] { -0.006929283f, -0.005336422f, -0.024047505f }));
        return List.of(-0.006929283f, -0.005336422f, -0.024047505f);
    }

    private void assertEmbeddingRequest(List<String> inputs) {
        assertEmbeddingRequest(mock.embeddings().singleRequest(), inputs);
    }

    private static void assertEmbeddingRequest(CapturedRequest request, List<String> inputs) {
        assertWatsonxRequest(request, URL_WATSONX_EMBEDDING_API);
        assertJsonBody(request, new EmbeddingPayload(DEFAULT_EMBEDDING_MODEL, null, PROJECT_ID, inputs, null, null));
    }
}
