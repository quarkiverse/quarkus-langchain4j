package io.quarkiverse.langchain4j.redis;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Json;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.redis.runtime.RedisFilterMapper;
import io.quarkiverse.langchain4j.redis.runtime.RedisSchema;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import io.quarkus.redis.datasource.search.CreateArgs;
import io.quarkus.redis.datasource.search.Document;
import io.quarkus.redis.datasource.search.QueryArgs;
import io.quarkus.redis.datasource.search.SearchQueryResponse;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.Command;
import io.vertx.mutiny.redis.client.Request;

public class RedisEmbeddingStore implements EmbeddingStore<TextSegment> {

    public static final String EXTRA_ATTRIBUTES = "extra_attributes";
    public static final String ID = "id";
    private final ReactiveRedisDataSource ds;
    private final RedisSchema schema;
    private final Logger LOG = Logger.getLogger(RedisEmbeddingStore.class);
    private final boolean indexCreated;
    private boolean warnedAboutWrongDimension = false;

    private static final String SCORE_FIELD_NAME = "vector_score";

    public static Builder builder() {
        return new Builder();
    }

    public RedisEmbeddingStore(ReactiveRedisDataSource ds, RedisSchema schema) {
        this.ds = ds;
        this.schema = schema;
        this.indexCreated = createIndexIfDoesNotExist();
    }

    private boolean createIndexIfDoesNotExist() {
        List<String> indexes = ds.search().ft_list()
                .onFailure().invoke(t -> {
                    if (t.getMessage().contains("unknown command")) {
                        LOG.error(
                                "The Redis server does not seem to support RediSearch. Please install the RediSearch module. " +
                                        "If using containers, we suggest to use the redis/redis-stack images.");
                    }
                }).await().indefinitely();
        if (!indexes.contains(schema.getIndexName())) {
            CreateArgs indexCreateArgs = new CreateArgs()
                    .onJson()
                    .prefixes(schema.getPrefix());
            schema.defineFields(indexCreateArgs);
            LOG.debug("Creating Redis index " + schema.getIndexName() + " with arguments: " + indexCreateArgs.toArgs());
            ds.search().ftCreate(schema.getIndexName(), indexCreateArgs).await().indefinitely();
            return true;
        } else {
            LOG.debug("Index in Redis already exists: " + schema.getIndexName());
            return false;
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
        int size = ids.size();
        List<Request> commands = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = embedded == null ? null : embedded.get(i);
            Map<String, Object> fields = new HashMap<>();
            fields.put(schema.getVectorFieldName(), embedding.vector());
            if (!warnedAboutWrongDimension && indexCreated && embedding.vector().length != schema.getDimension()) {
                LOG.warn("Creating an embedding with dimension " + embedding.vector().length + " but the index was " +
                        "created with dimension " + schema.getDimension() + ". " +
                        "This may result in embeddings not being found when they should be. " +
                        "Please check the quarkus.langchain4j.redis.dimension property. " +
                        "This warning will be shown only once.");
                warnedAboutWrongDimension = true;
            }
            if (textSegment != null) {
                fields.put(schema.getScalarFieldName(), textSegment.text());
                fields.putAll(textSegment.metadata().toMap());
            }
            String key = schema.getPrefix() + id;
            commands.add(Request.cmd(Command.JSON_SET).arg(key).arg("$").arg(Json.toJson(fields)));
        }
        ds.getRedis().batchAndAwait(commands);
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults,
            double minScore) {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
        return search(request).matches();

    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        String preFilterQuery = new RedisFilterMapper().map(request.filter());
        String queryTemplate = "%s=>[ KNN %d @%s $BLOB AS %s ]";
        String query = format(queryTemplate, preFilterQuery, request.maxResults(),
                schema.getVectorFieldName(), SCORE_FIELD_NAME);
        QueryArgs args = new QueryArgs()
                .sortByAscending(SCORE_FIELD_NAME)
                .param("BLOB", request.queryEmbedding().vector())
                .dialect(2);
        Uni<SearchQueryResponse> search = ds.search()
                .ftSearch(schema.getIndexName(), query, args);
        SearchQueryResponse response = search.await().indefinitely();
        List<EmbeddingMatch<TextSegment>> matches = response.documents().stream().map(this::extractEmbeddingMatch)
                .filter(embeddingMatch -> embeddingMatch.score() >= request.minScore())
                .collect(toList());
        return new EmbeddingSearchResult<>(matches);
    }

    private EmbeddingMatch<TextSegment> extractEmbeddingMatch(Document document) {
        try {
            JsonNode jsonNode = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER
                    .readTree(document.property("$").asString());
            JsonNode embedded = jsonNode.get(schema.getScalarFieldName());
            Embedding embedding = new Embedding(
                    Json.fromJson(jsonNode.get(schema.getVectorFieldName()).toString(), float[].class));
            double score = (2 - document.property(SCORE_FIELD_NAME).asDouble()) / 2;
            String id = document.key().substring(schema.getPrefix().length());
            Map<String, Object> textualMetadata = schema.getTextualMetadataFields().stream()
                    .filter(jsonNode::has)
                    .collect(Collectors.toMap(metadataFieldName -> metadataFieldName,
                            (name) -> jsonNode.get(name).asText()));
            Map<String, Object> numericMetadata = schema.getNumericMetadataFields().stream()
                    .filter(jsonNode::has)
                    .collect(Collectors.toMap(metadataFieldName -> metadataFieldName,
                            (name) -> {
                                if (jsonNode.get(name).isLong()) {
                                    return jsonNode.get(name).asLong();
                                } else {
                                    return jsonNode.get(name).asDouble();
                                }
                            }));
            Map<String, Object> allMetadata = new HashMap<>();
            allMetadata.putAll(textualMetadata);
            allMetadata.putAll(numericMetadata);
            TextSegment textSegment = embedded != null ? new TextSegment(embedded.asText(), Metadata.from(allMetadata)) : null;
            return new EmbeddingMatch<>(score, id, embedding, textSegment);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Deletes all keys with the prefix that is used by this embedding store.
     */
    public void deleteAll() {
        KeyScanArgs args = new KeyScanArgs().match(schema.getPrefix() + "*");
        Set<String> keysToDelete = ds.key().scan(args).toMulti().collect().asSet().await().indefinitely();
        if (!keysToDelete.isEmpty()) {
            Request command = Request.cmd(Command.DEL);
            keysToDelete.forEach(command::arg);
            ds.getRedis().send(command).await().indefinitely();
            LOG.debug("Deleted " + keysToDelete.size() + " keys");
        }
    }

    public static class Builder {

        private ReactiveRedisDataSource redisClient;

        private RedisSchema schema;

        public Builder dataSource(ReactiveRedisDataSource client) {
            this.redisClient = client;
            return this;
        }

        public Builder schema(RedisSchema schema) {
            this.schema = schema;
            return this;
        }

        public RedisEmbeddingStore build() {
            return new RedisEmbeddingStore(redisClient, schema);
        }

    }
}
