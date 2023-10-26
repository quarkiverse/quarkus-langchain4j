package io.quarkiverse.langchain4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.spi.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodecFactory;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodec;

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
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toJson(InMemoryEmbeddingStore<?> store) {
            try {
                return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.writeValueAsString(store);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
