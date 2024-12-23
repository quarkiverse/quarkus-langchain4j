package io.quarkiverse.langchain4j.chroma;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.randomUUID;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.ws.rs.WebApplicationException;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.ClientLogger;
import org.jboss.resteasy.reactive.client.api.LoggingScope;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.chroma.runtime.AddEmbeddingsRequest;
import io.quarkiverse.langchain4j.chroma.runtime.ChromaCollectionsRestApi;
import io.quarkiverse.langchain4j.chroma.runtime.Collection;
import io.quarkiverse.langchain4j.chroma.runtime.CreateCollectionRequest;
import io.quarkiverse.langchain4j.chroma.runtime.DeleteEmbeddingsRequest;
import io.quarkiverse.langchain4j.chroma.runtime.QueryRequest;
import io.quarkiverse.langchain4j.chroma.runtime.QueryResponse;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

/**
 * Represents a store for embeddings using the Chroma backend.
 * Always uses cosine distance as the distance metric.
 * <p>
 * TODO: introduce an SPI in langchain4j that will allow us to provide our own client
 */
public class ChromaEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final ChromaClient chromaClient;
    private final LazyValue<String> collectionId;

    /**
     * Initializes a new instance of ChromaEmbeddingStore with the specified parameters.
     *
     * @param baseUrl The base URL of the Chroma service.
     * @param collectionName The name of the collection in the Chroma service. If not specified, "default" will be used.
     * @param timeout The timeout duration for the Chroma client. If not specified, 5 seconds will be used.
     * @param logRequests Whether to log requests.
     * @param logResponses Whether to log responses.
     */
    public ChromaEmbeddingStore(String baseUrl, String collectionName, Duration timeout,
            boolean logRequests, boolean logResponses) {
        String effectiveCollectionName = getOrDefault(collectionName, "default");

        this.chromaClient = new ChromaClient(baseUrl, getOrDefault(timeout, ofSeconds(5)), logRequests, logResponses);

        this.collectionId = new LazyValue<>(new Supplier<String>() {
            @Override
            public String get() {
                Collection collection = chromaClient.collection(effectiveCollectionName);
                if (collection == null) {
                    Collection createdCollection = chromaClient
                            .createCollection(new CreateCollectionRequest(effectiveCollectionName));
                    return createdCollection.getId();
                } else {
                    return collection.getId();
                }
            }
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String collectionName;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;

        /**
         * @param baseUrl The base URL of the Chroma service.
         * @return builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param collectionName The name of the collection in the Chroma service. If not specified, "default" will be used.
         * @return builder
         */
        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        /**
         * @param timeout The timeout duration for the Chroma client. If not specified, 5 seconds will be used.
         * @return builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public ChromaEmbeddingStore build() {
            return new ChromaEmbeddingStore(this.baseUrl, this.collectionName, this.timeout, logRequests, logResponses);
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
                .map(embedding -> randomUUID())
                .collect(toList());

        addAllInternal(ids, embeddings, null);

        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {

        List<String> ids = embeddings.stream()
                .map(embedding -> randomUUID())
                .collect(toList());

        addAllInternal(ids, embeddings, textSegments);

        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        addAllInternal(ids, embeddings, embedded);
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAllInternal(singletonList(id), singletonList(embedding), textSegment == null ? null : singletonList(textSegment));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        AddEmbeddingsRequest addEmbeddingsRequest = AddEmbeddingsRequest.builder()
                .embeddings(embeddings.stream()
                        .map(Embedding::vector)
                        .collect(toList()))
                .ids(ids)
                .metadatas(textSegments == null
                        ? null
                        : textSegments.stream()
                                .map(TextSegment::metadata)
                                .map(Metadata::asMap)
                                .collect(toList()))
                .documents(textSegments == null
                        ? null
                        : textSegments.stream()
                                .map(TextSegment::text)
                                .collect(toList()))
                .build();

        chromaClient.addEmbeddings(collectionId.get(), addEmbeddingsRequest);
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        QueryRequest queryRequest = new QueryRequest(referenceEmbedding.vectorAsList(), maxResults);

        QueryResponse queryResponse = chromaClient.queryCollection(collectionId.get(), queryRequest);

        List<EmbeddingMatch<TextSegment>> matches = toEmbeddingMatches(queryResponse);

        return matches.stream()
                .filter(match -> match.score() >= minScore)
                .collect(toList());
    }

    // FIXME: we need to know the dimension to be able to construct a query that retrieves
    // all IDs of embeddings in the collection. Is there a better way to do this, without
    // having to pass the dimension explicitly?
    public void deleteAll(int dimension) {
        chromaClient.deleteAllEmbeddings(collectionId.get(), dimension);
    }

    private static List<EmbeddingMatch<TextSegment>> toEmbeddingMatches(QueryResponse queryResponse) {
        List<EmbeddingMatch<TextSegment>> embeddingMatches = new ArrayList<>();

        for (int i = 0; i < queryResponse.getIds().get(0).size(); i++) {

            double score = distanceToScore(queryResponse.getDistances().get(0).get(i));
            String embeddingId = queryResponse.getIds().get(0).get(i);
            Embedding embedding = Embedding.from(queryResponse.getEmbeddings().get(0).get(i));
            TextSegment textSegment = toTextSegment(queryResponse, i);

            embeddingMatches.add(new EmbeddingMatch<>(score, embeddingId, embedding, textSegment));
        }
        return embeddingMatches;
    }

    /**
     * By default, cosine distance will be used. For details: <a href="https://docs.trychroma.com/usage-guide"></a>
     * Converts a cosine distance in the range [0, 2] to a score in the range [0, 1].
     *
     * @param distance The distance value.
     * @return The converted score.
     */
    private static double distanceToScore(double distance) {
        return 1 - (distance / 2);
    }

    private static TextSegment toTextSegment(QueryResponse queryResponse, int i) {
        String text = queryResponse.getDocuments().get(0).get(i);
        Map<String, String> metadata = queryResponse.getMetadatas().get(0).get(i);
        return text == null ? null : TextSegment.from(text, metadata == null ? new Metadata() : new Metadata(metadata));
    }

    private static class ChromaClient {

        private final ChromaCollectionsRestApi chromaApi;

        ChromaClient(String baseUrl, Duration timeout, boolean logRequests, boolean logResponses) {
            try {
                var builder = QuarkusRestClientBuilder.newBuilder()
                        .baseUri(new URI(baseUrl))
                        .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                        .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS);

                if (logRequests || logResponses) {
                    builder.loggingScope(LoggingScope.REQUEST_RESPONSE);
                    builder.clientLogger(new ChromaEmbeddingStore.ChromaClientLogger(logRequests, logResponses));
                }

                chromaApi = builder.build(ChromaCollectionsRestApi.class);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        Collection createCollection(CreateCollectionRequest createCollectionRequest) {
            return chromaApi.createCollection(createCollectionRequest);
        }

        Collection collection(String collectionName) {
            try {
                return chromaApi.collection(collectionName);
            } catch (WebApplicationException e) {
                // if collection is not present, Chroma returns: Status - 500
                return null;
            }
        }

        boolean addEmbeddings(String collectionId, AddEmbeddingsRequest addEmbeddingsRequest) {
            return chromaApi.addEmbeddings(collectionId, addEmbeddingsRequest);
        }

        QueryResponse queryCollection(String collectionId, QueryRequest queryRequest) {
            return chromaApi.queryCollection(collectionId, queryRequest);
        }

        public void deleteAllEmbeddings(String collectionId, int dimension) {
            List<Float> referenceEmbedding = new ArrayList<>();
            for (int i = 0; i < dimension; i++) {
                referenceEmbedding.add(0.0f);
            }
            QueryRequest queryRequest = new QueryRequest(referenceEmbedding, Integer.MAX_VALUE);
            QueryResponse queryResponse = chromaApi.queryCollection(collectionId, queryRequest);
            if (!queryResponse.getIds().get(0).isEmpty()) {
                DeleteEmbeddingsRequest request = new DeleteEmbeddingsRequest(queryResponse.getIds().get(0));
                List<String> deletedIds = chromaApi.deleteEmbeddings(collectionId, request);
                // TODO: why do we have to do this twice? for some reason
                // embeddings sometimes remain in the db after the first delete,
                // even though the response says they were deleted
                chromaApi.deleteEmbeddings(collectionId, request);
            }
        }
    }

    static class ChromaClientLogger implements ClientLogger {
        private static final Logger log = Logger.getLogger(ChromaClientLogger.class);

        private final boolean logRequests;
        private final boolean logResponses;

        public ChromaClientLogger(boolean logRequests, boolean logResponses) {
            this.logRequests = logRequests;
            this.logResponses = logResponses;
        }

        @Override
        public void setBodySize(int bodySize) {
            // ignore
        }

        @Override
        public void logRequest(HttpClientRequest request, Buffer body, boolean omitBody) {
            if (!logRequests || !log.isInfoEnabled()) {
                return;
            }
            try {
                log.infof("Request:\n- method: %s\n- url: %s\n- headers: %s\n- body: %s",
                        request.getMethod(),
                        request.absoluteURI(),
                        inOneLine(request.headers()),
                        bodyToString(body));
            } catch (Exception e) {
                log.warn("Failed to log request", e);
            }
        }

        @Override
        public void logResponse(HttpClientResponse response, boolean redirect) {
            if (!logResponses || !log.isInfoEnabled()) {
                return;
            }
            response.bodyHandler(new io.vertx.core.Handler<>() {
                @Override
                public void handle(Buffer body) {
                    try {
                        log.infof(
                                "Response:\n- status code: %s\n- headers: %s\n- body: %s",
                                response.statusCode(),
                                inOneLine(response.headers()),
                                bodyToString(body));
                    } catch (Exception e) {
                        log.warn("Failed to log response", e);
                    }
                }
            });
        }

        private String bodyToString(Buffer body) {
            if (body == null) {
                return "";
            }
            return body.toString();
        }

        private String inOneLine(io.vertx.core.MultiMap headers) {

            return stream(headers.spliterator(), false)
                    .map(header -> {
                        String headerKey = header.getKey();
                        String headerValue = header.getValue();
                        return String.format("[%s: %s]", headerKey, headerValue);
                    })
                    .collect(joining(", "));
        }

    }
}
