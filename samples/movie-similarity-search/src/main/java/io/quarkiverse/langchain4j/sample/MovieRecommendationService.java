package io.quarkiverse.langchain4j.sample;

import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class MovieRecommendationService {

  @Inject
  EmbeddingStore<TextSegment> embeddingStore;

  @Inject 
  EmbeddingModel embeddingModel;

  @Transactional
  public List<Movie> searchSimilarMovies(String overview) {
    
    Embedding embedding = embeddingModel.embed(overview).content();
    EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    .queryEmbedding(embedding)
    .minScore(0.5)
    .maxResults(10)
    .build();

    return embeddingStore.search(request).matches().stream().map(m -> {
      Long id = m.embedded().metadata().getLong("id");
      Movie movie = Movie.findById(id);
      return movie;
    }).toList();

  }
}

