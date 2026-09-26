package io.quarkiverse.langchain4j.opensearch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.opensearch.OpenSearchEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class OpenSearchEmbeddingStoreTest extends EmbeddingStoreIT {

    private static final String INDEX_NAME = "opensearch-test";

    // The OpenSearch instance is started by Dev Services; the injected server-url property
    // is needed only to force a refresh after clearing, because removal is near-realtime.
    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.opensearch.server-url")
    String serverUrl;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(OpenSearchEmbeddingStoreTest::archive)
            .overrideRuntimeConfigKey("quarkus.langchain4j.opensearch.index-name", INDEX_NAME);

    @Inject
    OpenSearchEmbeddingStore embeddingStore;

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
        // OpenSearchEmbeddingStore.removeAll() drops the whole index and add() does not
        // recreate it, while the IT base asserts on an empty store right after clearing.
        // Drop the index to discard everything written by the previous test, then
        // re-create an empty index by seeding one embedding through addAll (which
        // creates the index) and deleting it again by id, then refresh so the removal
        // is visible to the search performed by ensureStoreIsEmpty().
        embeddingStore.removeAll();
        String seedId = UUID.randomUUID().toString();
        Embedding seed = embeddingModel().embed("index-init").content();
        embeddingStore.addAll(List.of(seedId), List.of(seed), null);
        embeddingStore.removeAll(List.of(seedId));
        refreshIndex();
    }

    private void refreshIndex() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/" + INDEX_NAME + "/_refresh"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to refresh index " + INDEX_NAME, e);
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    private static JavaArchive archive() {
        return ShrinkWrap.create(JavaArchive.class);
    }
}
