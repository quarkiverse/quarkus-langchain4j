package io.quarkiverse.langchain4j.mongodb;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.search.SearchPath.fieldPath;
import static com.mongodb.client.model.search.VectorSearchOptions.approximateVectorSearchOptions;

import java.util.*;

import org.bson.BinaryVector;
import org.bson.Document;
import org.bson.Float32BinaryVector;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.SearchIndexModel;
import com.mongodb.client.model.SearchIndexType;
import com.mongodb.client.model.search.FieldSearchPath;
import com.mongodb.client.model.search.VectorSearchOptions;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.mongodb.runtime.SimilaritySearch;

public class MongoDBEmbeddingStore implements EmbeddingStore<TextSegment> {
    private static final String ID = "_id";

    private final String indexName;
    private final String textFieldName;
    private final String scoreFieldName;
    private final String vectorFieldName;
    private final String metadataFieldName;
    private final MongoCollection<Document> collection;
    private final MongoDBFilterMapper filterMapper;

    public MongoDBEmbeddingStore(MongoClient mongoClient, String database, String collection, String indexName,
            String vectorFieldName, String textFieldName, int dimensions, SimilaritySearch similaritySearch,
            String scoreFieldName, String metadataFieldName) {
        this.indexName = indexName;
        this.textFieldName = textFieldName;
        this.scoreFieldName = scoreFieldName;
        this.vectorFieldName = vectorFieldName;
        this.metadataFieldName = metadataFieldName;
        this.filterMapper = new MongoDBFilterMapper(metadataFieldName);

        var collectionExists = collectionExistsViaListCollections(mongoClient, database, collection);

        if (!collectionExists) {
            mongoClient.getDatabase(database).createCollection(collection);
        }
        this.collection = mongoClient.getDatabase(database).getCollection(collection);

        if (!searchIndexExists(indexName)) {
            createSearchIndex(vectorFieldName, dimensions, similaritySearch);
        }

    }

    private boolean searchIndexExists(String indexName) {
        for (Document index : collection.listSearchIndexes()) {
            if (indexName.equals(index.getString("name"))) {
                return true;
            }
        }
        return false;
    }

    private void createSearchIndex(String vectorFieldName, int dimensions, SimilaritySearch similaritySearch) {
        Bson definition = new Document(
                "fields",
                Collections.singletonList(
                        new Document("type", "vector")
                                .append("path", vectorFieldName)
                                .append("numDimensions", dimensions)
                                .append("similarity", similaritySearch.value())));
        SearchIndexModel indexModel = new SearchIndexModel(
                this.indexName,
                definition,
                SearchIndexType.vectorSearch());
        this.collection.createSearchIndexes(Collections.singletonList(indexModel));
    }

    @Override
    public String add(Embedding embedding) {
        var id = ObjectId.get().toHexString();
        this.add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        this.add(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        var id = ObjectId.get().toHexString();
        add(id, embedding, textSegment);
        return id;
    }

    public void add(String id, Embedding embedding, TextSegment textSegment) {
        addAllInternal(Collections.singletonList(id), Collections.singletonList(embedding),
                textSegment == null ? null : Collections.singletonList(textSegment));
    }

    @Override
    public void removeAll() {
        collection.deleteMany(new Document());
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream()
                .map(ignored -> ObjectId.get().toHexString())
                .toList();
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> ObjectId.get().toHexString())
                .toList();
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        this.addAllInternal(ids, embeddings, embedded);
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (ids.isEmpty() || ids.size() != embeddings.size() || (embedded != null && embedded.size() != embeddings.size())) {
            throw new IllegalArgumentException("ids, embeddings and embedded must be non-empty and of the same size");
        }
        int size = ids.size();
        List<Document> documents = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = embedded == null ? null : embedded.get(i);

            documents.add(new Document()
                    .append(ID, id)
                    .append(vectorFieldName, BinaryVector.floatVector(embedding.vector()))
                    .append(textFieldName, textSegment != null ? textSegment.text() : null)
                    .append(metadataFieldName, textSegment != null ? textSegment.metadata().toMap() : null));
        }
        collection.insertMany(documents);
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        var filter = request.filter();
        var minScore = request.minScore();
        var maxResults = request.maxResults();
        var embedding = request.queryEmbedding();

        var queryVector = BinaryVector.floatVector(embedding.vector());
        FieldSearchPath fieldSearchPath = fieldPath(vectorFieldName);

        VectorSearchOptions options = approximateVectorSearchOptions(Math.max(100, maxResults * 10));



        // Create the vectorSearch pipeline stage
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(vectorSearch(
                fieldSearchPath,
                queryVector,
                indexName,
                maxResults,
                options));
        pipeline.add(project(
                fields(
                        include(ID),
                        include(textFieldName),
                        include(vectorFieldName),
                        include(metadataFieldName),
                        metaVectorSearchScore(scoreFieldName))));


        if (filter != null) {
            Bson mongoFilter = filterMapper.map(filter);
            if (mongoFilter != null) {
                pipeline.add(match(mongoFilter));
            }
        }

        pipeline.add(match(gte(scoreFieldName, minScore)));

        try (var iteratorResult = collection.aggregate(pipeline).cursor()) {
            List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
            while (iteratorResult.hasNext()) {
                var document = iteratorResult.tryNext();
                if (document != null) {
                    var score = document.getDouble(scoreFieldName);
                    var id = document.getString(ID);
                    var text = document.getString(textFieldName);
                    TextSegment textSegment = null;
                    var vectorData = document.get(vectorFieldName, Float32BinaryVector.class);
                    var vector = vectorData.getData();
                    var metadata = document.get(metadataFieldName, Document.class);

                    if (text != null) {
                        textSegment = TextSegment.from(text, Metadata.from(metadata));
                    }

                    result.add(new EmbeddingMatch<>(score, id, Embedding.from(vector), textSegment));
                }
            }
            return new EmbeddingSearchResult<>(result);
        }
    }

    public static boolean collectionExistsViaListCollections(MongoClient client, String dbName, String collectionName) {
        var db = client.getDatabase(dbName);

        try (var cursor = db.listCollections()
                .filter(new Document("name", collectionName))
                .cursor()) {
            return cursor.hasNext();
        }
    }
}
