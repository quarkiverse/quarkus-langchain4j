package io.quarkiverse.langchain4j.pgvector;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import java.sql.*;
import java.util.*;

import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.agroal.api.AgroalDataSource;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkus.logging.Log;

/**
 * PGVector EmbeddingStore Implementation
 * <p>
 * Only cosine similarity is used.
 * Only ivfflat index is used.
 */
public class PgVectorEmbeddingStore implements EmbeddingStore<TextSegment> {

    ObjectMapper objectMapper = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER;
    private static final Logger log = LoggerFactory.getLogger(PgVectorEmbeddingStore.class);
    private static final TypeReference<Map<String, String>> typeReference = new TypeReference<Map<String, String>>() {
    };
    private final AgroalDataSource datasource;
    private final String table;
    private Statement statement;

    /**
     * All args constructor for PgVectorEmbeddingStore Class
     *
     * @param datasource , the datasource object
     * @param table The database table
     * @param dimension The vector dimension
     * @param useIndex Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
     * @param indexListSize The IVFFlat number of lists
     * @param createTable Should create table automatically
     * @param dropTableFirst Should drop table first, usually for testing
     */
    public PgVectorEmbeddingStore(
            AgroalDataSource datasource,
            String table,
            Integer dimension,
            Boolean useIndex,
            Integer indexListSize,
            Boolean createTable,
            Boolean dropTableFirst) {
        this.datasource = datasource;
        this.table = ensureNotBlank(table, "table");

        useIndex = getOrDefault(useIndex, false);
        createTable = getOrDefault(createTable, true);
        dropTableFirst = getOrDefault(dropTableFirst, false);
        try (Connection connection = setupConnection()) {
            if (dropTableFirst) {
                statement = connection.createStatement();
                statement.executeUpdate(String.format("DROP TABLE IF EXISTS %s", table));
                statement.close();
            }

            if (createTable) {
                statement = connection.createStatement();
                statement.executeUpdate(String.format(
                        "CREATE TABLE IF NOT EXISTS %s (" +
                                "embedding_id UUID PRIMARY KEY, " +
                                "embedding vector(%s), " +
                                "text TEXT NULL, " +
                                "metadata JSON NULL" +
                                ")",
                        table, ensureGreaterThanZero(dimension, "dimension")));
                statement.close();
            }

            if (useIndex) {
                statement = connection.createStatement();
                statement.executeUpdate(String.format(
                        "CREATE INDEX IF NOT EXISTS ON %s " +
                                "USING ivfflat (embedding vector_cosine_ops) " +
                                "WITH (lists = %s)",
                        table, ensureGreaterThanZero(indexListSize, "indexListSize")));
                statement.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Connection setupConnection() throws SQLException {
        Connection connection = datasource.getConnection();
        try {
            statement = connection.createStatement();
            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");
            statement.close();
        } catch (PSQLException exception) {
            if (exception.getMessage().contains("could not open extension control file")) {
                Log.error(
                        "The PostgreSQL server does not seem to support pgvector."
                                + "If using containers/devservices we suggest to use quarkus.datasource.devservices.image-name=ankane/pgvector:v0.5.1");
            } else {
                throw exception;
            }
        }

        PGvector.addVectorType(connection);
        return connection;
    }

    public void deleteAll() throws SQLException {
        try (Connection connection = setupConnection()) {
            statement = connection.createStatement();
            statement.executeUpdate(String.format("TRUNCATE TABLE %s", table));
            statement.close();
        }
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param embedding The embedding to be added to the store.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        addInternal(id, embedding, null);
        return id;
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param id The unique identifier for the embedding to be added.
     * @param embedding The embedding to be added to the store.
     */
    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    /**
     * Adds a given embedding and the corresponding content that has been embedded to the store.
     *
     * @param embedding The embedding to be added to the store.
     * @param textSegment Original content that was embedded.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    /**
     * Adds multiple embeddings to the store.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    /**
     * Adds multiple embeddings and their corresponding contents that have been embedded to the store.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @param embedded A list of original contents that were embedded.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    /**
     * Finds the most relevant (closest in space) embeddings to the provided reference embedding.
     *
     * @param referenceEmbedding The embedding used as a reference. Returned embeddings should be relevant (closest) to this
     *        one.
     * @param maxResults The maximum number of embeddings to be returned.
     * @param minScore The minimum relevance score, ranging from 0 to 1 (inclusive).
     *        Only embeddings with a score of this value or higher will be returned.
     * @return A list of embedding matches.
     *         Each embedding match includes a relevance score (derivative of cosine distance),
     *         ranging from 0 (not relevant) to 1 (highly relevant).
     */
    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        try (Connection connection = setupConnection()) {
            String referenceVector = Arrays.toString(referenceEmbedding.vector());
            String query = String.format(
                    "WITH temp AS (SELECT (2 - (embedding <=> '%s')) / 2 AS score, embedding_id, embedding, text, metadata FROM %s) SELECT * FROM temp WHERE score >= %s ORDER BY score desc LIMIT %s;",
                    referenceVector, table, minScore, maxResults);
            PreparedStatement selectStmt = connection.prepareStatement(query);

            ResultSet resultSet = selectStmt.executeQuery();
            while (resultSet.next()) {
                double score = resultSet.getDouble("score");
                String embeddingId = resultSet.getString("embedding_id");

                PGvector vector = (PGvector) resultSet.getObject("embedding");
                Embedding embedding = new Embedding(vector.toArray());

                String text = resultSet.getString("text");
                TextSegment textSegment = null;
                if (isNotNullOrBlank(text)) {
                    String metadataJson = Optional.ofNullable(resultSet.getString("metadata")).orElse("{}");
                    Map<String, String> metadataMap = objectMapper.readValue(metadataJson, typeReference);
                    Metadata metadata = new Metadata(new HashMap<>(metadataMap));
                    textSegment = TextSegment.from(text, metadata);
                }
                result.add(new EmbeddingMatch<>(score, embeddingId, embedding, textSegment));
            }
            selectStmt.close();
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(
                singletonList(id),
                singletonList(embedding),
                embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(
            List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isCollectionEmpty(ids) || isCollectionEmpty(embeddings)) {
            log.info("Empty embeddings - no ops");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");

        try (Connection connection = setupConnection()) {
            String query = String.format(
                    "INSERT INTO %s (embedding_id, embedding, text, metadata) VALUES (?, ?, ?, ?)" +
                            "ON CONFLICT (embedding_id) DO UPDATE SET " +
                            "embedding = EXCLUDED.embedding," +
                            "text = EXCLUDED.text," +
                            "metadata = EXCLUDED.metadata;",
                    table);

            PreparedStatement upsertStmt = connection.prepareStatement(query);

            for (int i = 0; i < ids.size(); ++i) {
                upsertStmt.setObject(1, UUID.fromString(ids.get(i)));
                upsertStmt.setObject(2, new PGvector(embeddings.get(i).vector()));

                if (embedded != null && embedded.get(i) != null) {
                    upsertStmt.setObject(3, embedded.get(i).text());
                    Map<String, String> metadata = embedded.get(i).metadata().asMap();
                    upsertStmt.setObject(4, objectMapper.writeValueAsString(metadata), Types.OTHER);
                } else {
                    upsertStmt.setNull(3, Types.VARCHAR);
                    upsertStmt.setNull(4, Types.OTHER);
                }
                upsertStmt.addBatch();
            }

            upsertStmt.executeBatch();
            upsertStmt.close();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
