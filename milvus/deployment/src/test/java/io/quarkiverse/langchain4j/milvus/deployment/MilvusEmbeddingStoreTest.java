package io.quarkiverse.langchain4j.milvus.deployment;

import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;

public class MilvusEmbeddingStoreTest extends EmbeddingStoreWithoutMetadataIT {

    public static final String COLLECTION_NAME = "test_embeddings";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.langchain4j.milvus.collection-name=" + COLLECTION_NAME + "\n" +
                                    "quarkus.langchain4j.milvus.devservices.port=19530\n" +
                                    "quarkus.langchain4j.milvus.dimension=384"),
                            "application.properties"));

    @Inject
    MilvusEmbeddingStore embeddingStore;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected void clearStore() {
        // make sure the bean is initialized, so the collection is created
        embeddingStore.toString();
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost("localhost")
                .withPort(19530)
                .build();
        MilvusClient client = new MilvusServiceClient(connectParam);
        try {
            client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(COLLECTION_NAME).build());
            R<MutationResult> deleteResult = client.delete(DeleteParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    // seems we can't just say "delete all entries", but
                    // can provide a predicate that is always false
                    .withExpr("id != 'BLABLA'")
                    .build());
            Log.info("Deleted: " + deleteResult.getData().getDeleteCnt());
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } finally {
            client.close();
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

}
