package io.quarkiverse.langchain4j.sample.chatbot;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class CsvIngestorExample {

    /**
     * The embedding store (the database).
     * The bean is provided by the quarkus-langchain4j-redis extension.
     */
    @Inject
    RedisEmbeddingStore store;

    /**
     * The embedding model (how the vector of a document is computed).
     * The bean is provided by the LLM (like openai) extension.
     */
    @Inject
    EmbeddingModel embeddingModel;

    @ConfigProperty(name = "csv.file")
    File file;

    @ConfigProperty(name = "csv.headers")
    List<String> headers;

    public void ingest(@Observes StartupEvent event) throws IOException {
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(headers.toArray(new String[0]))
                .setSkipHeaderRecord(true)
                .build();
        List<Document> documents = new ArrayList<>();
        try (Reader reader = new FileReader(file)) {
            // Generate on document per row, the document is using the following syntax:
            // key1: value1
            // key2: value2
            Iterable<CSVRecord> records = csvFormat.parse(reader);
            int i = 1;
            for (CSVRecord record : records) {

                Map<String, String> metadata = new HashMap<>();
                metadata.put("source", file.getAbsolutePath());
                metadata.put("row", String.valueOf(i++));

                StringBuilder content = new StringBuilder();
                for (String header : headers) {
                    metadata.put(header, record.get(header)); // Include all headers in the metadata.
                    content.append(header).append(": ").append(record.get(header)).append("\n");
                }
                documents.add(new Document(content.toString(), Metadata.from(metadata)));
            }

            var ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingStore(store)
                    .embeddingModel(embeddingModel)
                    .documentSplitter(recursive(500, 0))
                    .build();
            ingestor.ingest(documents);
            System.out.printf("Ingested %d documents.%n", documents.size());

        }

    }
}
