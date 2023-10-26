package io.quarkiverse.langchain4j.runtime.jackson;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

public class InMemoryEmbeddingStoreDeserializer extends StdDeserializer<InMemoryEmbeddingStore<TextSegment>> {

    public InMemoryEmbeddingStoreDeserializer() {
        super(InMemoryEmbeddingStore.class);
    }

    public InMemoryEmbeddingStoreDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public InMemoryEmbeddingStore<TextSegment> deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        return ctxt.readValue(p, InMemoryEmbeddingStoreMirror.class).toInMemoryEmbeddingStore();
    }

    private static class InMemoryEmbeddingStoreMirror {
        public List<InMemoryEmbeddingStoreEntryMirror> entries;

        public InMemoryEmbeddingStore<TextSegment> toInMemoryEmbeddingStore() {
            var result = new InMemoryEmbeddingStore<TextSegment>();
            for (var entry : entries) {
                result.add(entry.id, entry.embedding, entry.embedded);
            }
            return result;
        }
    }

    private static class InMemoryEmbeddingStoreEntryMirror {
        public String id;
        public Embedding embedding;
        public TextSegment embedded;
    }
}
