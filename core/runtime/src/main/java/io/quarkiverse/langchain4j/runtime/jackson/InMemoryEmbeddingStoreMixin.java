package io.quarkiverse.langchain4j.runtime.jackson;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkus.jackson.JacksonMixin;

@JacksonMixin(InMemoryEmbeddingStore.class)
@JsonDeserialize(using = InMemoryEmbeddingStoreDeserializer.class)
public abstract class InMemoryEmbeddingStoreMixin {
}
