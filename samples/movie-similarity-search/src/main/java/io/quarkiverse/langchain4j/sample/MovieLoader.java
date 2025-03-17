package io.quarkiverse.langchain4j.sample;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;

@ApplicationScoped
public class MovieLoader {


  public void load(@Observes StartupEvent event, @ConfigProperty(name = "movies.file") Path moviesFile,
                   EmbeddingStore embeddingStore, EmbeddingModel embeddingModel) throws Exception {
    if (!Files.exists(moviesFile)) {
      throw new IllegalStateException("Missing movies file: " + moviesFile);
    }

    embeddingStore.removeAll();

    EmbeddingStoreIngestor ingester = EmbeddingStoreIngestor.builder()
    .embeddingModel(embeddingModel)
    .embeddingStore(embeddingStore)
    .build();

    List<Document> docs = new ArrayList<>();

    Files.lines(moviesFile).skip(1).forEach(line -> {
      Movie movie = Movie.fromCsvLine(line);
      Long id = save(movie).id;
       
      Metadata metadata = Metadata.from(Map.of("id", id, "title", movie.title));
      Document document = Document.from(movie.overview, metadata);
      docs.add(document);
    });

    Log.info("Ingesting movies...");
    ingester.ingest(docs);
    Log.info("Application initalized!");
  }

  @Transactional
  public Movie save(Movie m) {
    m.persist();
    return m;
  }

}

