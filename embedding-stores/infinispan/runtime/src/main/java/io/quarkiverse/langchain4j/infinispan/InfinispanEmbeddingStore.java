package io.quarkiverse.langchain4j.infinispan;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.query.Query;
import org.infinispan.commons.configuration.StringConfiguration;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanSchema;
import io.quarkiverse.langchain4j.infinispan.runtime.LangchainInfinispanItem;

public class InfinispanEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final RemoteCache<String, LangchainInfinispanItem> remoteCache;
    private final InfinispanSchema schema;
    private static final String DEFAULT_CACHE_CONFIG = "<distributed-cache name=\"CACHE_NAME\">\n"
            + "<indexing storage=\"local-heap\">\n"
            + "<indexed-entities>\n"
            + "<indexed-entity>LANGCHAINITEM</indexed-entity>\n"
            + "</indexed-entities>\n"
            + "</indexing>\n"
            + "</distributed-cache>";

    public static Builder builder() {
        return new Builder();
    }

    public InfinispanEmbeddingStore(RemoteCacheManager cacheManager, InfinispanSchema schema) {
        String langchainCache = DEFAULT_CACHE_CONFIG.replace("CACHE_NAME", schema.getCacheName())
                .replace("LANGCHAINITEM", SchemaAndMarshallerProducer.LANGCHAIN_ITEM + schema.getDimension());
        this.remoteCache = cacheManager.administration()
                .getOrCreateCache(schema.getCacheName(), new StringConfiguration(langchainCache));
        this.schema = schema;
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        addAllInternal(ids, embeddings, embedded);
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (ids.isEmpty() || ids.size() != embeddings.size() || (embedded != null && embedded.size() != embeddings.size())) {
            throw new IllegalArgumentException("ids, embeddings and embedded must be non-empty and of the same size");
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        int size = ids.size();
        Map<String, LangchainInfinispanItem> elements = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = embedded == null ? null : embedded.get(i);
            if (textSegment != null) {
                Map<String, Object> map = textSegment.metadata().toMap();
                final List<String> metadataKeys = new ArrayList<>(map.size());
                final List<String> metadataValues = new ArrayList<>(map.size());
                map.entrySet().forEach(e -> {
                    metadataKeys.add(e.getKey());
                    metadataValues.add(e.getValue() != null ? e.getValue().toString() : null);
                });
                elements.put(id,
                        new LangchainInfinispanItem(id, embedding.vector(), textSegment.text(), metadataKeys, metadataValues));
            } else {
                elements.put(id, new LangchainInfinispanItem(id, embedding.vector(), null, null, null));
            }
        }
        // blocking call
        remoteCache.putAll(elements);
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Query<Object[]> query = remoteCache.query("select i, score(i) from " + "LangchainItem"
                + schema.getDimension().toString()
                + " i where i.floatVector <-> " + Arrays.toString(request.queryEmbedding().vector()) + "~"
                + schema.getDistance());
        List<Object[]> hits = query.maxResults(request.maxResults()).list();

        return new EmbeddingSearchResult<>(hits.stream().map(obj -> {
            LangchainInfinispanItem item = (LangchainInfinispanItem) obj[0];
            Float score = (Float) obj[1];
            if (score.doubleValue() < request.minScore()) {
                return null;
            }
            TextSegment embedded = null;
            if (item.getText() != null) {
                Map<String, Object> map = new HashMap<>();
                List<String> metadataKeys = item.getMetadataKeys();
                List<String> metadataValues = item.getMetadataValues();
                for (int i = 0; i < metadataKeys.size(); i++) {
                    map.put(metadataKeys.get(i), metadataValues.get(i));
                }
                embedded = new TextSegment(item.getText(), new Metadata(map));
            }
            Embedding embedding = new Embedding(item.getFloatVector());
            return new EmbeddingMatch<>(score.doubleValue(), item.getId(), embedding, embedded);
        }).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ids.forEach(id -> remoteCache.remove(id));
    }

    @Override
    public void removeAll() {
        remoteCache.clear();
    }

    public static class Builder {
        private RemoteCacheManager cacheManager;
        private InfinispanSchema schema;

        public Builder cacheManager(RemoteCacheManager client) {
            this.cacheManager = client;
            return this;
        }

        public Builder schema(InfinispanSchema schema) {
            this.schema = schema;
            return this;
        }

        public InfinispanEmbeddingStore build() {
            return new InfinispanEmbeddingStore(cacheManager, schema);
        }

    }
}
