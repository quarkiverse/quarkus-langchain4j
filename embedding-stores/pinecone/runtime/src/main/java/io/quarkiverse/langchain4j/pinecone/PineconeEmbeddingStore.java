package io.quarkiverse.langchain4j.pinecone;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import io.quarkiverse.langchain4j.pinecone.runtime.CreateIndexPodSpec;
import io.quarkiverse.langchain4j.pinecone.runtime.CreateIndexRequest;
import io.quarkiverse.langchain4j.pinecone.runtime.CreateIndexSpec;
import io.quarkiverse.langchain4j.pinecone.runtime.DistanceMetric;
import io.quarkiverse.langchain4j.pinecone.runtime.PineconeIndexOperationsApi;
import io.quarkiverse.langchain4j.pinecone.runtime.PineconeVectorOperationsApi;
import io.quarkiverse.langchain4j.pinecone.runtime.QueryRequest;
import io.quarkiverse.langchain4j.pinecone.runtime.QueryResponse;
import io.quarkiverse.langchain4j.pinecone.runtime.UpsertRequest;
import io.quarkiverse.langchain4j.pinecone.runtime.UpsertResponse;
import io.quarkiverse.langchain4j.pinecone.runtime.UpsertVector;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.logging.Log;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class PineconeEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final PineconeVectorOperationsApi vectorOperations;
    private final PineconeIndexOperationsApi indexOperations;
    private final String namespace;
    private final String textFieldName;
    private final String indexName;
    private final Integer dimension;
    private final LazyValue<Object> indexExists;

    public PineconeEmbeddingStore(String apiKey,
            String indexName,
            String projectId,
            String environment,
            String namespace,
            String textFieldName,
            Duration timeout,
            Integer dimension,
            String podType,
            Duration indexReadinessTimeout) {
        this.indexName = indexName;
        this.dimension = dimension;
        String baseUrl = "https://" + indexName + "-" + projectId + ".svc." + environment + ".pinecone.io";
        String baseUrlIndexOperations = "https://api.pinecone.io";
        try {
            ClientHeadersFactory clientHeadersFactory = new ClientHeadersFactory() {
                @Override
                public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incoming,
                        MultivaluedMap<String, String> outgoing) {
                    MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
                    headers.put("Api-Key", singletonList(apiKey));
                    return headers;
                }
            };
            vectorOperations = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(new URI(baseUrl))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .clientHeadersFactory(clientHeadersFactory)
                    .build(PineconeVectorOperationsApi.class);
            indexOperations = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(new URI(baseUrlIndexOperations))
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .clientHeadersFactory(clientHeadersFactory)
                    .build(PineconeIndexOperationsApi.class);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        this.namespace = namespace;
        this.textFieldName = textFieldName;
        Log.info("PineconeEmbeddingStore using base URL: " + baseUrl);
        this.indexExists = new LazyValue<>(new Supplier<Object>() {
            @Override
            public Object get() {
                if (indexOperations.listIndexes().getIndexes().stream().anyMatch(i -> i.getName().equals(indexName))) {
                    Log.info("Pinecone index " + indexName + " already exists");
                } else {
                    if (dimension == null) {
                        throw new IllegalArgumentException(
                                "quarkus.langchain4j.pinecone.dimension must be specified when creating a new index");
                    }
                    CreateIndexSpec spec = new CreateIndexSpec(new CreateIndexPodSpec(environment, podType));
                    indexOperations.createIndex(new CreateIndexRequest(indexName, dimension, DistanceMetric.COSINE, spec));
                    Log.info("Created Pinecone index " + indexName + " with dimension = " + dimension + ", " +
                            "now waiting for it to be become ready...");
                    waitForIndexToBecomeReady(indexName, indexReadinessTimeout);
                }
                return new Object();
            }
        });
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

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        indexExists.get();
        QueryRequest queryRequest = new QueryRequest(namespace, (long) request.maxResults(), true, true,
                request.queryEmbedding().vector());
        QueryResponse response = vectorOperations.query(queryRequest);
        return new EmbeddingSearchResult<>(response
                .getMatches().stream().map(match -> {
                    String text = match.getMetadata() != null &&
                            match.getMetadata().get(textFieldName) != null
                                    ? match.getMetadata().get(textFieldName)
                                    : null;
                    return new EmbeddingMatch<>(
                            RelevanceScore.fromCosineSimilarity(match.getScore()),
                            match.getId(),
                            new Embedding(match.getValues()),
                            text != null ? new TextSegment(
                                    text,
                                    new Metadata(mapWithoutKey(match.getMetadata(), textFieldName))) : null);
                })
                .filter(match -> match.score() >= request.minScore())
                .collect(toList()));
    }

    public PineconeVectorOperationsApi getUnderlyingClient() {
        return vectorOperations;
    }

    public Map<String, String> mapWithoutKey(Map<String, String> input, String key) {
        return input.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(key))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAllInternal(singletonList(id), singletonList(embedding), textSegment == null ? null : singletonList(textSegment));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        indexExists.get();
        Log.debug("Adding embeddings: " + embeddings);
        int count = ids.size();
        List<UpsertVector> vectorList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UpsertVector vector = new UpsertVector.Builder()
                    .id(ids.get(i))
                    .value(embeddings.get(i).vector())
                    .metadata(textFieldName, textSegments == null ? null : textSegments.get(i).text())
                    .metadata(textSegments != null ? textSegments.get(i).metadata().toMap() : null)
                    .build();
            vectorList.add(vector);
        }
        UpsertRequest request = new UpsertRequest(vectorList, namespace);
        UpsertResponse response = vectorOperations.upsert(request);
        Log.debug("Added embeddings: " + response.getUpsertedCount());
    }

    private void waitForIndexToBecomeReady(String indexName, Duration timeout) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeout.toMillis()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (indexOperations.describeIndex(indexName).getStatus().isReady()) {
                Log.info("Pinecone index " + indexName + " is now ready");
                return;
            }
        }
        throw new RuntimeException("Index " + indexName + " did not become ready within " + timeout);
    }

}
