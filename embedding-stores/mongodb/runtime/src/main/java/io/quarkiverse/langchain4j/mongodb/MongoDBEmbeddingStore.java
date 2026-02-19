package io.quarkiverse.langchain4j.mongodb;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.search.SearchPath.fieldPath;
import static com.mongodb.client.model.search.VectorSearchOptions.exactVectorSearchOptions;
import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.List;

import org.bson.BinaryVector;
import org.bson.Document;
import org.bson.Float32BinaryVector;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.search.FieldSearchPath;
import com.mongodb.client.model.search.VectorSearchOptions;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;

public class MongoDBEmbeddingStore implements EmbeddingStore<TextSegment> {
    private static final String ID = "_id";
    private static final String SCORE = "score";

    private final String indexName;
    private final String vectorFieldName;
    private final String textFieldName;
    private final String metadataFieldName;
    private final MongoCollection<Document> collection;

    public MongoDBEmbeddingStore(MongoClient mongoClient, String database, String collection, String indexName,
            String vectorFieldName, String textFieldName, String metadataFieldName) {
        this.indexName = indexName;
        this.vectorFieldName = vectorFieldName;
        this.textFieldName = textFieldName;
        this.metadataFieldName = metadataFieldName;
        this.collection = mongoClient.getDatabase(database).getCollection(collection);
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
        Document document = new Document()
                .append(ID, id)
                .append(vectorFieldName, embedding.vector())
                .append(textFieldName, textSegment != null ? textSegment.text() : null)
                .append(metadataFieldName, textSegment != null ? textSegment.metadata() : null);
        collection.insertOne(document);
    }

    @Override
    public List<String> addAll(List<Embedding> list) {
        return List.of();
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest embeddingSearchRequest) {
        var filter = embeddingSearchRequest.filter();
        var maxResults = embeddingSearchRequest.maxResults();
        var minScore = embeddingSearchRequest.minScore();
        var embedding = embeddingSearchRequest.queryEmbedding();

        var queryVector = BinaryVector.floatVector(embedding.vector());
        FieldSearchPath fieldSearchPath = fieldPath(vectorFieldName);

        VectorSearchOptions options = exactVectorSearchOptions()
                .filter(gte("year", 2016));

        // Create the vectorSearch pipeline stage
        List<Bson> pipeline = asList(
                vectorSearch(
                        fieldSearchPath,
                        queryVector,
                        indexName,
                        maxResults,
                        options),
                project(
                        fields(
                                include(ID),
                                include(textFieldName),
                                include(vectorFieldName),
                                include(metadataFieldName),
                                metaVectorSearchScore(SCORE))),
                match(gte(SCORE, minScore)));
        var iteratorResult = collection.aggregate(pipeline).iterator();
        List<EmbeddingMatch<TextSegment>> result;
        var availableResults = iteratorResult.available();
        if (availableResults > 0) {
            result = new ArrayList<>(availableResults);
            iteratorResult.forEachRemaining(document -> {
                var score = document.getDouble(SCORE);
                var id = document.getString(ID);
                var text = document.getString(textFieldName);
                var vector = document.get(vectorFieldName, Float32BinaryVector.class);
                result.add(new EmbeddingMatch<>(score, id, Embedding.from(vector.getData()), TextSegment.from(text)));
            });
        } else
            result = List.of();
        return new EmbeddingSearchResult<>(result);
    }
}
