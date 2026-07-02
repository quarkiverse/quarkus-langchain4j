package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.embedding.EmbeddingModel;

@ApplicationScoped
public class MovieLoader {

  public void load(@Observes StartupEvent event, @ConfigProperty(name = "movies.file") Path moviesFile,
                   HibernateEmbeddingStore<Movie> embeddingStore, EmbeddingModel embeddingModel) throws Exception {
    if (!Files.exists(moviesFile)) {
      throw new IllegalStateException("Missing movies file: " + moviesFile);
    }

    embeddingStore.removeAll();

    List<Movie> movies;
    try (Stream<String> moviesStream = Files.lines(moviesFile)) {
      movies = moviesStream.skip(1).map(Movie::fromCsvLine).collect(Collectors.toList());
    }

    List<TextSegment> textSegments = new ArrayList<>(movies.size());
    for (Movie movie : movies) {
      Metadata metadata = Metadata.from(Map.of("title", movie.title, "link", movie.link));
      textSegments.add(TextSegment.from(movie.overview, metadata));
    }

    List<Embedding> embeddings = embeddingModel.embedAll(textSegments).content();
    for (int i = 0; i < embeddings.size(); i++) {
      movies.get(i).embedding = embeddings.get(i).vector();
    }

    Log.info("Ingesting movies...");
    embeddingStore.addAllEntities(movies);
    Log.info("Application initalized!");
  }

}

