package io.quarkiverse.langchain4j.infinispan;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import dev.langchain4j.store.embedding.filter.Filter;
import io.quarkiverse.langchain4j.infinispan.runtime.InfinispanSchema;
import io.quarkiverse.langchain4j.infinispan.runtime.LangchainInfinispanItem;
import io.quarkiverse.langchain4j.infinispan.runtime.LangchainMetadata;

/**
 * Stores and retrieves embeddings using Infinispan Server as the backend.
 * Supports vector similarity search with optional metadata filtering,
 * and removal of embeddings by ID or by metadata filter.
 */
public class InfinispanEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final RemoteCache<String, LangchainInfinispanItem> remoteCache;
    private final InfinispanSchema schema;
    private final String langchainItemType;
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
        this.schema = schema;
        this.langchainItemType = SchemaAndMarshallerProducer.LANGCHAIN_ITEM + schema.getDimension();
        if (schema.isCreateCache()) {
            String cacheConfig = schema.getCacheConfig();
            if (cacheConfig == null) {
                cacheConfig = DEFAULT_CACHE_CONFIG
                        .replace("CACHE_NAME", schema.getCacheName())
                        .replace("LANGCHAINITEM", langchainItemType);
            }
            this.remoteCache = cacheManager.administration()
                    .getOrCreateCache(schema.getCacheName(), new StringConfiguration(cacheConfig));
        } else {
            this.remoteCache = cacheManager.getCache(schema.getCacheName());
        }
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
                Set<LangchainMetadata> metadata = textSegment.metadata().toMap().entrySet().stream()
                        .map(e -> new LangchainMetadata(e.getKey(), e.getValue()))
                        .collect(Collectors.toSet());
                elements.put(id,
                        new LangchainInfinispanItem(id, embedding.vector(), textSegment.text(), metadata,
                                textSegment.metadata().toMap()));
            } else {
                elements.put(id, new LangchainInfinispanItem(id, embedding.vector(), null, null, null));
            }
        }
        // blocking call
        remoteCache.putAll(elements);
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        InfinispanMetadataFilterMapper.FilterResult filteringQuery = new InfinispanMetadataFilterMapper()
                .map(request.filter());
        String joinPart = "";
        String filteringPart = "";

        if (filteringQuery != null) {
            joinPart = filteringQuery.join;
            filteringPart = " filtering(" + filteringQuery.query + ")";
        }

        String vectorQuery = "select i, score(i) from " + langchainItemType
                + " i "
                + joinPart
                + " where i.floatVector <-> " + Arrays.toString(request.queryEmbedding().vector()) + "~"
                + schema.getDistance()
                + filteringPart;

        Query<Object[]> query = remoteCache.query(vectorQuery);
        List<Object[]> hits = query.maxResults(request.maxResults()).list();

        return new EmbeddingSearchResult<>(hits.stream().map(obj -> {
            LangchainInfinispanItem item = (LangchainInfinispanItem) obj[0];
            Float score = (Float) obj[1];
            if (score.doubleValue() < request.minScore()) {
                return null;
            }
            TextSegment textSegment = null;
            if (item.getText() != null) {
                Map<String, Object> map = new HashMap<>();
                if (item.getMetadata() != null) {
                    for (LangchainMetadata metadata : item.getMetadata()) {
                        map.put(metadata.getName(), metadata.getValue());
                    }
                }
                textSegment = new TextSegment(item.getText(), new Metadata(map));
            }
            Embedding embedding = new Embedding(item.getFloatVector());
            return new EmbeddingMatch<>(score.doubleValue(), item.getId(), embedding, textSegment);
        }).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids cannot be null or empty");
        }
        ids.forEach(id -> remoteCache.remove(id));
    }

    @Override
    public void removeAll(Filter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("filter cannot be null");
        }

        InfinispanMetadataFilterMapper.FilterResult filterResult = new InfinispanMetadataFilterMapper().map(filter);

        String deleteQuery = "DELETE FROM " + langchainItemType
                + " i " + filterResult.join + " where " + filterResult.query;
        Query<LangchainInfinispanItem> query = remoteCache.query(deleteQuery);
        query.execute();
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
