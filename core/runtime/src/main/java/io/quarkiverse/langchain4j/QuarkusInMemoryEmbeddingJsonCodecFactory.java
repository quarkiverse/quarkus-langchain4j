package io.quarkiverse.langchain4j;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.spi.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodecFactory;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodec;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

public class QuarkusInMemoryEmbeddingJsonCodecFactory implements InMemoryEmbeddingStoreJsonCodecFactory {
    @Override
    public InMemoryEmbeddingStoreJsonCodec create() {
        return new Codec();
    }

    private static class Codec implements InMemoryEmbeddingStoreJsonCodec {

        private static final TypeReference<InMemoryEmbeddingStore<TextSegment>> TYPE_REFERENCE = new TypeReference<>() {
        };

        @Override
        public InMemoryEmbeddingStore<TextSegment> fromJson(String json) {
            try {
                return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(json, TYPE_REFERENCE);
            } catch (JacksonException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toJson(InMemoryEmbeddingStore<?> store) {
            try {
                return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.writeValueAsString(store);
            } catch (JacksonException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
