package io.quarkiverse.langchain4j.runtime.jackson;

import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkus.jackson.JacksonMixin;
import tools.jackson.databind.annotation.JsonDeserialize;

@JacksonMixin(InMemoryEmbeddingStore.class)
@JsonDeserialize(using = InMemoryEmbeddingStoreDeserializer.class)
public abstract class InMemoryEmbeddingStoreMixin {
}
