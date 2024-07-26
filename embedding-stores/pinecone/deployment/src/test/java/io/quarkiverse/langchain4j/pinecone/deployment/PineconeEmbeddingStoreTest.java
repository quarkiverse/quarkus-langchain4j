package io.quarkiverse.langchain4j.pinecone.deployment;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
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
 * These are set as GitHub secrets in the main repository. Because we only
 * have one account in the starter region and one shared Pinecone index, we
 * can't run this test multiple times in parallel, and so to prevent
 * unnecessary failures, this test currently runs only in the nightly build.
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
//@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
@Disabled("This is pretty unstable and making our nightly builds useless")
public class PineconeEmbeddingStoreTest extends EmbeddingStoreIT {

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
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void awaitUntilPersisted() {
        delay();
    }

    @Override
    protected void clearStore() {
        Log.info("About to delete all embeddings");
        PineconeVectorOperationsApi client = embeddingStore.getUnderlyingClient();
        float[] vector = new float[384];
        QueryRequest allRequest = new QueryRequest(null, 10000L, false, false, vector);
        List<String> existingEntries = client.query(allRequest).getMatches().stream().map(VectorMatch::getId).toList();
        if (!existingEntries.isEmpty()) {
            Log.info("Deleting " + existingEntries.size() + " embeddings: " + existingEntries);
            client.delete(new DeleteRequest(existingEntries, false, null, null));
        }
        delay();
    }

    /**
     * Seems we have to add some delay after each insert/delete operation
     * before Pinecone fully processes the operation.
     */
    private static void delay() {
        try {
            int timeout = 20;
            Log.info("Waiting " + timeout + " seconds to allow Pinecone time to process deletions");
            TimeUnit.SECONDS.sleep(timeout);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
