package io.quarkiverse.langchain4j.hibernate.test.multi;

import org.junit.jupiter.api.Disabled;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;

// disabled because test methods are run manually in other test code
@Disabled
public class BaseTest extends EmbeddingStoreWithoutMetadataIT {
    private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    public BaseTest(EmbeddingStore<TextSegment> hibernateEmbeddingStore) {
        this.hibernateEmbeddingStore = hibernateEmbeddingStore;
    }

    protected EmbeddingStore<TextSegment> hibernateEmbeddingStore;

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        hibernateEmbeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return hibernateEmbeddingStore;
    }

    @Override
    protected void should_add_embedding() {
        super.should_add_embedding();
    }

    @Override
    protected void should_add_embedding_with_id() {
        super.should_add_embedding_with_id();
    }

    @Override
    protected void should_add_multiple_embeddings() {
        super.should_add_multiple_embeddings();
    }

    @Override
    protected void should_add_multiple_embeddings_with_segments() {
        super.should_add_multiple_embeddings_with_segments();
    }

    @Override
    protected void should_add_multiple_embeddings_with_ids_and_segments() {
        super.should_add_multiple_embeddings_with_ids_and_segments();
    }

    @Override
    protected void should_find_with_min_score() {
        super.should_find_with_min_score();
    }

    @Override
    protected void should_return_correct_score() {
        super.should_return_correct_score();
    }

}
