package io.quarkiverse.langchain4j.infinispan;

import org.infinispan.client.hotrod.RemoteCacheManager;

import dev.langchain4j.store.embedding.infinispan.InfinispanStoreConfiguration;

public class InfinispanEmbeddingStore extends dev.langchain4j.store.embedding.infinispan.InfinispanEmbeddingStore {
    public InfinispanEmbeddingStore() {
        super();
    }

    public InfinispanEmbeddingStore(RemoteCacheManager remoteCacheManager,
            InfinispanStoreConfiguration storeConfiguration) {
        super(remoteCacheManager, storeConfiguration);
    }

    public void deleteAll() {
        remoteCache().clearAsync();
    }
}
