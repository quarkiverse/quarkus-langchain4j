package io.quarkiverse.langchain4j.lancedb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.complex.FixedSizeListVector;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.lance.namespace.LanceNamespace;
import org.lance.namespace.model.CreateTableRequest;
import org.lance.namespace.model.DeleteFromTableRequest;
import org.lance.namespace.model.InsertIntoTableRequest;
import org.lance.namespace.model.QueryTableRequest;
import org.lance.namespace.model.QueryTableRequestColumns;
import org.lance.namespace.model.QueryTableRequestVector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;

public class LanceDbEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final String ID_COLUMN = "id";
    private static final String VECTOR_COLUMN = "vector";
    private static final String TEXT_COLUMN = "text";
    private static final String METADATA_COLUMN = "metadata";

    private final LanceNamespace namespace;
    private final String tableName;
    private final int dimension;
    private final Schema schema;
    private final DistanceType distanceType;
    private final LanceDbFilterMapper filterMapper;
    private final ObjectMapper objectMapper;
    private volatile boolean tableCreated = false;

    public LanceDbEmbeddingStore(LanceNamespace namespace, String tableName, int dimension) {
        this(namespace, tableName, dimension, DistanceType.l2);
    }

    public LanceDbEmbeddingStore(LanceNamespace namespace, String tableName, int dimension, DistanceType distanceType) {
        this.namespace = namespace;
        this.tableName = tableName;
        this.dimension = dimension;
        this.distanceType = distanceType;
        this.schema = createSchema(dimension);
        this.filterMapper = new LanceDbFilterMapper();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String add(Embedding embedding) {
        String id = generateId();
        addInternal(List.of(id), List.of(embedding), List.of());
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(List.of(id), List.of(embedding), List.of());
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = generateId();
        addInternal(List.of(id), List.of(embedding),
                textSegment != null ? List.of(textSegment) : List.of());
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream()
                .map(e -> generateId())
                .toList();
        addInternal(ids, embeddings, List.of());
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        List<String> ids = embeddings.stream()
                .map(e -> generateId())
                .toList();
        addInternal(ids, embeddings, textSegments != null ? textSegments : List.of());
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        ensureTableCreated();

        QueryTableRequest query = new QueryTableRequest();
        query.setId(Arrays.asList(tableName));
        query.setK(request.maxResults());
        query.setVectorColumn(VECTOR_COLUMN);

        List<Float> queryVector = toFloatList(request.queryEmbedding().vector());
        QueryTableRequestVector vector = new QueryTableRequestVector();
        vector.setSingleVector(queryVector);
        query.setVector(vector);

        if (request.filter() != null) {
            String filterString = filterMapper.map(request.filter());
            if (filterString != null) {
                query.setFilter(filterString);
                query.setPrefilter(true);
            }
        }

        QueryTableRequestColumns columns = new QueryTableRequestColumns();
        columns.setColumnNames(Arrays.asList(ID_COLUMN, VECTOR_COLUMN, TEXT_COLUMN, METADATA_COLUMN));
        query.setColumns(columns);

        try {
            byte[] resultBytes = namespace.queryTable(query);
            List<EmbeddingMatch<TextSegment>> matches = parseQueryResult(resultBytes, request.minScore());
            return new EmbeddingSearchResult<>(matches);
        } catch (Exception e) {
            throw new RuntimeException("Failed to search LanceDB", e);
        }
    }

    @Override
    public void remove(String id) {
        ensureTableCreated();
        try {
            DeleteFromTableRequest request = new DeleteFromTableRequest();
            request.setId(Arrays.asList(tableName));
            request.setPredicate(ID_COLUMN + " = '" + id + "'");
            namespace.deleteFromTable(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove from LanceDB", e);
        }
    }

    private void addInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        ensureTableCreated();
        byte[] arrowData = createArrowData(ids, embeddings, textSegments);
        try {
            InsertIntoTableRequest request = new InsertIntoTableRequest();
            request.setId(Arrays.asList(tableName));
            request.setMode("append");
            namespace.insertIntoTable(request, arrowData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add embedding to LanceDB", e);
        }
    }

    private synchronized void ensureTableCreated() {
        if (tableCreated) {
            return;
        }
        try {
            byte[] emptyData = createEmptyArrowData();
            CreateTableRequest request = new CreateTableRequest();
            request.setId(Arrays.asList(tableName));
            namespace.createTable(request, emptyData);
            tableCreated = true;
        } catch (Exception e) {
            if (isTableAlreadyExistsError(e)) {
                tableCreated = true;
            } else {
                throw new RuntimeException("Failed to create table in LanceDB", e);
            }
        }
    }

    private boolean isTableAlreadyExistsError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                message = e.getCause().getMessage();
            } else {
                return false;
            }
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("already exists") || lowerMessage.contains("duplicate")
                || lowerMessage.contains("conflict");
    }

    private Schema createSchema(int dim) {
        return new Schema(Arrays.asList(
                new Field(ID_COLUMN, FieldType.nullable(new ArrowType.Utf8()), null),
                new Field(VECTOR_COLUMN,
                        FieldType.nullable(new ArrowType.FixedSizeList(dim)),
                        Arrays.asList(new Field("item",
                                FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)),
                                null))),
                new Field(TEXT_COLUMN, FieldType.nullable(new ArrowType.Utf8()), null),
                new Field(METADATA_COLUMN, FieldType.nullable(new ArrowType.Binary()), null)));
    }

    private byte[] createArrowData(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        try (BufferAllocator allocator = new RootAllocator();
                VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {

            VarCharVector idVector = (VarCharVector) root.getVector(ID_COLUMN);
            FixedSizeListVector vectorVector = (FixedSizeListVector) root.getVector(VECTOR_COLUMN);
            Float4Vector vectorData = (Float4Vector) vectorVector.getDataVector();
            VarCharVector textVector = (VarCharVector) root.getVector(TEXT_COLUMN);
            VarBinaryVector metadataVector = (VarBinaryVector) root.getVector(METADATA_COLUMN);

            int rowCount = ids.size();
            root.setRowCount(rowCount);

            for (int i = 0; i < rowCount; i++) {
                idVector.setSafe(i, ids.get(i).getBytes());

                vectorVector.setNotNull(i);
                float[] vector = embeddings.get(i).vector();
                for (int j = 0; j < dimension; j++) {
                    vectorData.setSafe(i * dimension + j, vector[j]);
                }

                if (i < textSegments.size() && textSegments.get(i) != null) {
                    TextSegment segment = textSegments.get(i);
                    textVector.setSafe(i, segment.text().getBytes());
                    byte[] metaBytes = serializeMetadata(segment);
                    if (metaBytes != null) {
                        metadataVector.setSafe(i, metaBytes);
                    } else {
                        metadataVector.setNull(i);
                    }
                } else {
                    textVector.setNull(i);
                    metadataVector.setNull(i);
                }
            }

            idVector.setValueCount(rowCount);
            vectorData.setValueCount(rowCount * dimension);
            vectorVector.setValueCount(rowCount);
            textVector.setValueCount(rowCount);
            metadataVector.setValueCount(rowCount);

            return serializeToArrowIpc(root);
        }
    }

    private byte[] createEmptyArrowData() {
        try (BufferAllocator allocator = new RootAllocator();
                VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
            root.setRowCount(0);
            return serializeToArrowIpc(root);
        }
    }

    private byte[] serializeToArrowIpc(VectorSchemaRoot root) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ArrowStreamWriter writer = new ArrowStreamWriter(root, null, Channels.newChannel(out))) {
            writer.start();
            writer.writeBatch();
            writer.end();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize Arrow data", e);
        }
        return out.toByteArray();
    }

    private List<EmbeddingMatch<TextSegment>> parseQueryResult(byte[] resultBytes, double minScore) {
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        try (BufferAllocator allocator = new RootAllocator();
                ArrowFileReader reader = new ArrowFileReader(
                        new ByteArraySeekableByteChannel(resultBytes), allocator)) {

            for (int i = 0; i < reader.getRecordBlocks().size(); i++) {
                reader.loadRecordBatch(reader.getRecordBlocks().get(i));
                VectorSchemaRoot root = reader.getVectorSchemaRoot();

                VarCharVector idVector = (VarCharVector) root.getVector(ID_COLUMN);
                FixedSizeListVector vectorVector = (FixedSizeListVector) root.getVector(VECTOR_COLUMN);
                Float4Vector vectorData = (Float4Vector) vectorVector.getDataVector();
                VarCharVector textVector = (VarCharVector) root.getVector(TEXT_COLUMN);
                VarBinaryVector metadataVector = (VarBinaryVector) root.getVector(METADATA_COLUMN);

                Float4Vector distanceVector = null;
                try {
                    distanceVector = (Float4Vector) root.getVector("_distance");
                } catch (IllegalArgumentException ignored) {
                }

                for (int row = 0; row < root.getRowCount(); row++) {
                    String id = new String(idVector.get(row));

                    float[] embedding = new float[dimension];
                    for (int j = 0; j < dimension; j++) {
                        embedding[j] = vectorData.get(row * dimension + j);
                    }

                    TextSegment textSegment = null;
                    if (!textVector.isNull(row)) {
                        String text = new String(textVector.get(row));
                        byte[] metaBytes = metadataVector.isNull(row) ? null : metadataVector.get(row);
                        textSegment = deserializeTextSegment(text, metaBytes);
                    }

                    double score;
                    if (distanceVector != null && row < distanceVector.getValueCount() && !distanceVector.isNull(row)) {
                        float distance = distanceVector.get(row);
                        score = distanceType.toRelevanceScore(distance);
                    } else {
                        score = 1.0;
                    }

                    if (score >= minScore) {
                        matches.add(new EmbeddingMatch<>(score, id, new Embedding(embedding), textSegment));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LanceDB query result", e);
        }
        return matches;
    }

    private byte[] serializeMetadata(TextSegment textSegment) {
        if (textSegment.metadata() == null) {
            return null;
        }
        Map<String, Object> map = textSegment.metadata().toMap();
        if (map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize metadata to JSON", e);
        }
    }

    private TextSegment deserializeTextSegment(String text, byte[] metaBytes) {
        if (metaBytes == null || metaBytes.length == 0) {
            return new TextSegment(text, dev.langchain4j.data.document.Metadata.from(Collections.emptyMap()));
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(metaBytes, HashMap.class);
            return new TextSegment(text, dev.langchain4j.data.document.Metadata.from(map));
        } catch (IOException e) {
            return fallbackDeserializeTextSegment(text, metaBytes);
        }
    }

    private TextSegment fallbackDeserializeTextSegment(String text, byte[] metaBytes) {
        String metaStr = new String(metaBytes);
        Map<String, Object> map = new HashMap<>();
        for (String entry : metaStr.split("\0")) {
            int eq = entry.indexOf('=');
            if (eq > 0) {
                map.put(entry.substring(0, eq), entry.substring(eq + 1));
            }
        }
        return new TextSegment(text, dev.langchain4j.data.document.Metadata.from(map));
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }

    public enum DistanceType {
        l2 {
            @Override
            public double toRelevanceScore(float distance) {
                return RelevanceScore.fromCosineSimilarity(1.0 - distance / 2);
            }
        },
        cosine {
            @Override
            public double toRelevanceScore(float distance) {
                return RelevanceScore.fromCosineSimilarity(1.0 - distance);
            }
        },
        ip {
            @Override
            public double toRelevanceScore(float distance) {
                return RelevanceScore.fromCosineSimilarity(distance);
            }
        };

        public abstract double toRelevanceScore(float distance);
    }

    private static class ByteArraySeekableByteChannel implements SeekableByteChannel {
        private final byte[] data;
        private long position = 0;
        private boolean isOpen = true;

        ByteArraySeekableByteChannel(byte[] data) {
            this.data = data;
        }

        @Override
        public int read(ByteBuffer dst) {
            int remaining = dst.remaining();
            int available = (int) (data.length - position);
            if (available <= 0) {
                return -1;
            }
            int toRead = Math.min(remaining, available);
            dst.put(data, (int) position, toRead);
            position += toRead;
            return toRead;
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public SeekableByteChannel position(long newPosition) {
            this.position = newPosition;
            return this;
        }

        @Override
        public long size() {
            return data.length;
        }

        @Override
        public boolean isOpen() {
            return isOpen;
        }

        @Override
        public void close() {
            isOpen = false;
        }

        @Override
        public int write(ByteBuffer src) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException();
        }
    }
}
