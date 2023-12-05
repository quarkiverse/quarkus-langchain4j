package io.quarkiverse.langchain4j.pinecone.deployment;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.RelevanceScore;
import io.quarkiverse.langchain4j.pinecone.PineconeEmbeddingStore;
import io.quarkiverse.langchain4j.pinecone.runtime.DeleteRequest;
import io.quarkiverse.langchain4j.pinecone.runtime.PineconeVectorOperationsApi;
import io.quarkiverse.langchain4j.pinecone.runtime.QueryRequest;
import io.quarkiverse.langchain4j.pinecone.runtime.VectorMatch;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Prerequisites for this test: A pinecone index must exist (can be in the starter region)
 * and the following environment variables must be set accordingly:
 * PINECONE_API_KEY, PINECONE_ENVIRONMENT, PINECONE_PROJECT_ID and PINECONE_INDEX_NAME
 *
 * These are set as GitHub secrets in the main repository. GitHub doesn't
 * pass them to workflows triggered from forks though, so this test only
 * runs with the nightly CI workflow, or for PRs submitted from the main
 * quarkiverse repository (NOT from a fork).
 *
 * <p>
 * Original data in the index will be lost during the test.
 * <p>
 * Because of delays in Pinecone when deleting vectors, the test adds
 * artificial delays (the `delay` method) to make sure we see the correct
 * data, and thus the test takes a relatively long time to run. If you see
 * intermittent failures, it may mean that the delay isn't long enough...
 *
 */
@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
public class PineconeEmbeddingStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            // to enable rest client logging:
                            "quarkus.rest-client.logging.scope=request-response\n" +
                                    "quarkus.rest-client.logging.body-limit=10000\n" +
                                    "quarkus.log.category.\"org.jboss.resteasy.reactive.client.logging\".level=DEBUG\n" +
                                    "quarkus.langchain4j.pinecone.api-key=${pinecone.api.key}\n" +
                                    "quarkus.langchain4j.pinecone.environment=${pinecone.environment}\n" +
                                    "quarkus.langchain4j.pinecone.project-id=${pinecone.project-id}\n" +
                                    "quarkus.langchain4j.pinecone.index-name=${pinecone.index-name}\n"),
                            "application.properties"));

    @Inject
    PineconeEmbeddingStore embeddingStore;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    public void cleanup() {
        // Normally we would use deleteAll=true for deleting all vectors,
        // but that doesn't work in the gcp-starter environment,
        // so make it a two-step process instead by querying for all vectors, and then removing them.
        Log.info("About to delete all embeddings");
        PineconeVectorOperationsApi client = embeddingStore.getUnderlyingClient();
        float[] vector = new float[384];
        QueryRequest allRequest = new QueryRequest(null, 10000L, false, false, vector);
        List<String> existingEntries = client.query(allRequest).getMatches().stream().map(VectorMatch::getId).toList();
        if (!existingEntries.isEmpty()) {
            Log.info("Deleting " + existingEntries.size() + " embeddings: " + existingEntries);
            client.delete(new DeleteRequest(existingEntries, false, null, null));
        }

    }

    /**
     * Seems we have to add some delay after each insert operation before Pinecone
     * processes the vector and makes it available for querying.
     */
    private static void delay() {
        try {
            int timeout = 40;
            Log.info("Waiting " + timeout + " seconds to allow Pinecone time to process deletions");
            TimeUnit.SECONDS.sleep(timeout);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void should_add_embedding() {
        Embedding embedding = embeddingModel.embed(randomUUID()).content();

        String id = embeddingStore.add(embedding);
        assertThat(id).isNotNull();

        delay();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();
    }

    @Test
    void should_add_embedding_with_id() {
        String id = randomUUID();
        Embedding embedding = embeddingModel.embed(randomUUID()).content();

        embeddingStore.add(id, embedding);
        delay();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();
    }

    @Test
    void should_add_embedding_with_segment() {
        TextSegment segment = TextSegment.from(randomUUID());
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);
        delay();
        assertThat(id).isNotNull();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isEqualTo(segment);
    }

    @Test
    void should_add_embedding_with_segment_with_metadata() {
        TextSegment segment = TextSegment.from(randomUUID(), Metadata.from("test-key", "test-value"));
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);

        assertThat(id).isNotNull();

        delay();
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isEqualTo(segment);
    }

    @Test
    void should_add_multiple_embeddings() {
        Embedding firstEmbedding = embeddingModel.embed(randomUUID()).content();
        Embedding secondEmbedding = embeddingModel.embed(randomUUID()).content();

        List<String> ids = embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));
        assertThat(ids).hasSize(2);
        delay();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);

        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        assertThat(firstMatch.embedded()).isNull();

        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isBetween(0d, 1d);
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        assertThat(secondMatch.embedding()).isEqualTo(secondEmbedding);
        assertThat(secondMatch.embedded()).isNull();
    }

    @Test
    void should_add_multiple_embeddings_with_segments() {
        TextSegment firstSegment = TextSegment.from(randomUUID());
        Embedding firstEmbedding = embeddingModel.embed(firstSegment.text()).content();
        TextSegment secondSegment = TextSegment.from(randomUUID());
        Embedding secondEmbedding = embeddingModel.embed(secondSegment.text()).content();

        List<String> ids = embeddingStore.addAll(
                asList(firstEmbedding, secondEmbedding),
                asList(firstSegment, secondSegment));
        assertThat(ids).hasSize(2);
        delay();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);

        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        assertThat(firstMatch.embedded()).isEqualTo(firstSegment);

        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isBetween(0d, 1d);
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        assertThat(secondMatch.embedding()).isEqualTo(secondEmbedding);
        assertThat(secondMatch.embedded()).isEqualTo(secondSegment);
    }

    @Test
    void should_find_with_min_score() {
        String firstId = randomUUID();
        Embedding firstEmbedding = embeddingModel.embed(randomUUID()).content();
        embeddingStore.add(firstId, firstEmbedding);

        String secondId = randomUUID();
        Embedding secondEmbedding = embeddingModel.embed(randomUUID()).content();
        embeddingStore.add(secondId, secondEmbedding);

        delay();
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);
        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(firstId);
        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isBetween(0d, 1d);
        assertThat(secondMatch.embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant2 = embeddingStore.findRelevant(
                firstEmbedding,
                10,
                secondMatch.score() - 0.01);
        assertThat(relevant2).hasSize(2);
        assertThat(relevant2.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant2.get(1).embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant3 = embeddingStore.findRelevant(
                firstEmbedding,
                10,
                secondMatch.score());
        assertThat(relevant3).hasSize(2);
        assertThat(relevant3.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant3.get(1).embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant4 = embeddingStore.findRelevant(
                firstEmbedding,
                10,
                secondMatch.score() + 0.01);
        assertThat(relevant4).hasSize(1);
        assertThat(relevant4.get(0).embeddingId()).isEqualTo(firstId);
    }

    @Test
    void should_return_correct_score() {
        Embedding embedding = embeddingModel.embed("hello").content();

        String id = embeddingStore.add(embedding);
        assertThat(id).isNotNull();

        Embedding referenceEmbedding = embeddingModel.embed("hi").content();

        delay();
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(referenceEmbedding, 1);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(embedding, referenceEmbedding)),
                withPercentage(1));
    }
}
