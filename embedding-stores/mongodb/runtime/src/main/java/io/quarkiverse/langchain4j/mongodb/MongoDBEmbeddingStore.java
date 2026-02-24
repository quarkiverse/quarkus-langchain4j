package io.quarkiverse.langchain4j.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.SearchIndexModel;
import com.mongodb.client.model.SearchIndexType;
import com.mongodb.client.model.search.FieldSearchPath;
import com.mongodb.client.model.search.VectorSearchOptions;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.mongodb.runtime.SimilaritySearch;
import org.bson.BinaryVector;
import org.bson.Document;
import org.bson.Float32BinaryVector;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.search.SearchPath.fieldPath;
import static com.mongodb.client.model.search.VectorSearchOptions.approximateVectorSearchOptions;

public class MongoDBEmbeddingStore implements EmbeddingStore<TextSegment> {
    private static final String ID = "_id";

    private final String indexName;
    private final String textFieldName;
    private final String scoreFieldName;
    private final String vectorFieldName;
    private final String metadataFieldName;
    private final MongoCollection<Document> collection;

    public MongoDBEmbeddingStore(MongoClient mongoClient, String database, String collection, String indexName,
                                 String vectorFieldName, String textFieldName, int dimensions, SimilaritySearch similaritySearch, String scoreFieldName,String metadataFieldName) {
        this.indexName = indexName;
        this.textFieldName = textFieldName;
        this.scoreFieldName = scoreFieldName;
        this.vectorFieldName = vectorFieldName;
        this.metadataFieldName = metadataFieldName;

        var collectionExists = collectionExistsViaListCollections(mongoClient, database, collection);

        if (!collectionExists) {
            mongoClient.getDatabase(database).createCollection(collection);
            this.collection = mongoClient.getDatabase(database).getCollection(collection);
            createSearchIndex(vectorFieldName, dimensions, similaritySearch);
        }else this.collection = mongoClient.getDatabase(database).getCollection(collection);

    }

    private void createSearchIndex(String vectorFieldName, int dimensions, SimilaritySearch similaritySearch) {
        Bson definition = new Document(
                "fields",
                Collections.singletonList(
                        new Document("type", "vector")
                                .append("path", vectorFieldName)
                                .append("numDimensions", dimensions)
                                .append("similarity", similaritySearch.value())
                )
        );
        SearchIndexModel indexModel = new SearchIndexModel(
                this.indexName,
                definition,
                SearchIndexType.vectorSearch()
        );
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
        Document document = new Document()
                .append(ID, id)
                .append(vectorFieldName, embedding.vector())
                .append(textFieldName, textSegment != null ? textSegment.text() : null)
                .append(metadataFieldName, textSegment != null ? textSegment.metadata() : null);
        collection.insertOne(document);
    }

    @Override
    public void removeAll() {
        collection.deleteMany(new Document());
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = new ArrayList<>(embeddings.size());
        List<Document> documents = new ArrayList<>(embeddings.size());
        for (Embedding embedding : embeddings) {
            var id = ObjectId.get().toHexString();
            ids.add(id);
            documents.add(new Document()
                    .append(ID, id)
                    .append(vectorFieldName, embedding.vector()));
        }
        collection.insertMany(documents);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = new ArrayList<>(embeddings.size());
        List<Document> documents = new ArrayList<>(embeddings.size());
        for (int i = 0; i < embeddings.size(); i++) {
            var id = ObjectId.get().toHexString();
            ids.add(id);
            TextSegment textSegment = embedded.get(i);
            var vector = embeddings.get(i).vector();

            documents.add(new Document()
                    .append(ID, id)
                    .append(vectorFieldName, BinaryVector.floatVector(vector))
                    .append(textFieldName, textSegment != null ? textSegment.text() : null)
                    .append(metadataFieldName, textSegment != null ? textSegment.metadata().toMap() : null));
        }
        collection.insertMany(documents);
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest embeddingSearchRequest) {
        var filter = embeddingSearchRequest.filter();
        var minScore = embeddingSearchRequest.minScore();
        var maxResults = embeddingSearchRequest.maxResults();
        var embedding = embeddingSearchRequest.queryEmbedding();

        var queryVector = BinaryVector.floatVector(embedding.vector());
        FieldSearchPath fieldSearchPath = fieldPath(vectorFieldName);

        VectorSearchOptions options = approximateVectorSearchOptions(Math.max(100, maxResults * 10));

        if (filter != null) {
        }

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
                                metaVectorSearchScore(scoreFieldName)
                        )
                )
        );
        pipeline.add(match(gte(scoreFieldName, minScore)));

        try(var iteratorResult = collection.aggregate(pipeline).cursor()) {
            List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
            while (iteratorResult.hasNext()) {
                var document = iteratorResult.tryNext();
                if (document != null) {
                    var score = document.getDouble(scoreFieldName);
                    var id = document.getString(ID);
                    var text = document.getString(textFieldName);
                    var vectorData = document.get(vectorFieldName, Float32BinaryVector.class);
                    var vector = vectorData.getData();
                    result.add(new EmbeddingMatch<>(score, id, Embedding.from(vector), TextSegment.from(text)));
                }
            }
            return new EmbeddingSearchResult<>(result);
        }
    }

    public static boolean collectionExistsViaListCollections(MongoClient client, String dbName, String collectionName) {
        var db = client.getDatabase(dbName);

        try(var cursor = db.listCollections()
                .filter(new Document("name", collectionName))
                .cursor()) {
            return cursor.hasNext();
        }
    }
}
