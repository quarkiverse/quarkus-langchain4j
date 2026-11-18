package io.quarkiverse.langchain4j.qdrant;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;

import jakarta.inject.Inject;

import org.awaitility.core.ThrowingRunnable;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import io.quarkiverse.langchain4j.qdrant.runtime.QdrantEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class QdrantEmbeddingStoreTest extends EmbeddingStoreIT {

    public static final String COLLECTION_NAME = "qdrant_test_embeddings";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(QdrantEmbeddingStoreTest::archive);

    @Inject
    QdrantEmbeddingStore embeddingStore;

    private static EmbeddingModel embeddingModel;

    /**
     * FIXME: This is a workaround to avoid loading the embedding model in this test class' static initializer,
     * because otherwise we hit
     * java.lang.UnsatisfiedLinkError: Native Library (/path/to/the/library) already loaded in another classloader
     * because the test class is loaded by JUnit and by Quarkus in different class loaders.
     */
    @BeforeAll
    public static void initEmbeddingModel() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    /**
     * The search request metadata filter must actually scope the result set. Two segments with identical text but
     * different {@code tenant_id} metadata are stored, and a search filtered to one tenant must return only that
     * tenant's segment. Before the filter was applied the search returned both, silently bypassing the filter.
     */
    @Test
    void should_apply_metadata_filter_on_search() {
        TextSegment firstTenant = TextSegment.from("the quick brown fox", new Metadata().put("tenant_id", 1));
        TextSegment secondTenant = TextSegment.from("the quick brown fox", new Metadata().put("tenant_id", 2));
        Embedding firstEmbedding = embeddingModel().embed(firstTenant).content();
        Embedding secondEmbedding = embeddingModel().embed(secondTenant).content();
        embeddingStore().add(firstEmbedding, firstTenant);
        embeddingStore().add(secondEmbedding, secondTenant);

        awaitUntilAsserted(new ThrowingRunnable() {
            @Override
            public void run() {
                assertThat(getAllEmbeddings()).hasSize(2);
            }
        });

        EmbeddingSearchRequest firstTenantOnly = EmbeddingSearchRequest.builder()
                .queryEmbedding(firstEmbedding)
                .maxResults(10)
                .filter(metadataKey("tenant_id").isEqualTo(1))
                .build();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore().search(firstTenantOnly).matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embedded().metadata().getInteger("tenant_id")).isEqualTo(1);
    }

    /**
     * A compound filter must be translated and applied as a whole. Only the segment that matches both the category
     * and the year range may be returned.
     */
    @Test
    void should_apply_compound_metadata_filter_on_search() {
        TextSegment recentNews = TextSegment.from("the quick brown fox",
                new Metadata().put("category", "news").put("year", 2024));
        TextSegment recentBlog = TextSegment.from("the quick brown fox",
                new Metadata().put("category", "blog").put("year", 2024));
        TextSegment oldNews = TextSegment.from("the quick brown fox",
                new Metadata().put("category", "news").put("year", 2019));
        Embedding recentNewsEmbedding = embeddingModel().embed(recentNews).content();
        embeddingStore().add(recentNewsEmbedding, recentNews);
        embeddingStore().add(embeddingModel().embed(recentBlog).content(), recentBlog);
        embeddingStore().add(embeddingModel().embed(oldNews).content(), oldNews);

        awaitUntilAsserted(new ThrowingRunnable() {
            @Override
            public void run() {
                assertThat(getAllEmbeddings()).hasSize(3);
            }
        });

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(recentNewsEmbedding)
                .maxResults(10)
                .filter(metadataKey("category").isEqualTo("news")
                        .and(metadataKey("year").isGreaterThanOrEqualTo(2020)))
                .build();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore().search(request).matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embedded().metadata().getString("category")).isEqualTo("news");
        assertThat(matches.get(0).embedded().metadata().getInteger("year")).isEqualTo(2024);
    }

    /**
     * langchain4j treats an {@code IsNotIn} filter as satisfied when the metadata key is absent, so a document that
     * lacks the key must still be returned. Three segments are stored: one whose tenant_id is excluded, one whose
     * tenant_id is allowed, and one with no tenant_id at all. Only the excluded tenant must be filtered out.
     */
    @Test
    void should_keep_documents_missing_the_key_on_is_not_in() {
        TextSegment excludedTenant = TextSegment.from("the quick brown fox", new Metadata().put("tenant_id", 1));
        TextSegment allowedTenant = TextSegment.from("the quick brown fox", new Metadata().put("tenant_id", 2));
        TextSegment noTenant = TextSegment.from("the quick brown fox", new Metadata().put("category", "news"));
        Embedding excludedEmbedding = embeddingModel().embed(excludedTenant).content();
        embeddingStore().add(excludedEmbedding, excludedTenant);
        embeddingStore().add(embeddingModel().embed(allowedTenant).content(), allowedTenant);
        embeddingStore().add(embeddingModel().embed(noTenant).content(), noTenant);

        awaitUntilAsserted(new ThrowingRunnable() {
            @Override
            public void run() {
                assertThat(getAllEmbeddings()).hasSize(3);
            }
        });

        EmbeddingSearchRequest notExcluded = EmbeddingSearchRequest.builder()
                .queryEmbedding(excludedEmbedding)
                .maxResults(10)
                .filter(metadataKey("tenant_id").isNotIn(1))
                .build();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore().search(notExcluded).matches();

        assertThat(matches).hasSize(2);
        boolean excludedTenantReturned = false;
        for (EmbeddingMatch<TextSegment> match : matches) {
            Metadata metadata = match.embedded().metadata();
            if (metadata.containsKey("tenant_id") && metadata.getInteger("tenant_id").equals(1)) {
                excludedTenantReturned = true;
            }
        }
        assertThat(excludedTenantReturned).isFalse();
    }

    /**
     * Qdrant's text match is tokenizer- and index-dependent and cannot reproduce langchain4j's case-sensitive
     * substring semantics, so a {@code ContainsString} filter is rejected rather than translated into a filter that
     * would quietly return the wrong documents.
     */
    @Test
    void should_reject_contains_string_filter() {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel().embed("the quick brown fox").content())
                .maxResults(10)
                .filter(metadataKey("title").containsString("news"))
                .build();

        try {
            embeddingStore().search(request);
            fail("A ContainsString filter must be rejected, not silently translated");
        } catch (UnsupportedOperationException expected) {
            assertThat(expected).hasMessageContaining("ContainsString");
        }
    }

    private static JavaArchive archive() {
        Asset properties = new StringAsset(String.join("\n",
                "quarkus.langchain4j.qdrant.collection-name=" + COLLECTION_NAME,
                "quarkus.langchain4j.qdrant.payload-text-key=custom_text_field",
                "quarkus.qdrant.devservices.collections." + COLLECTION_NAME + ".vector-size=384",
                "quarkus.qdrant.devservices.collections." + COLLECTION_NAME + ".distance=Cosine"));

        return ShrinkWrap.create(JavaArchive.class).addAsResource(properties, "application.properties");
    }

}
