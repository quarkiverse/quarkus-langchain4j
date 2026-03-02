package io.quarkiverse.langchain4j.sample;

import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.transaction.Transactional;
import org.hibernate.query.restriction.Restriction;

@ApplicationScoped
public class MovieRecommendationService {

  @Inject
  HibernateEmbeddingStore<Movie> embeddingStore;

  @Inject 
  EmbeddingModel embeddingModel;

  @Transactional
  public List<Movie> searchSimilarMovies(String overview) {
    Embedding embedding = embeddingModel.embed(overview).content();
    return embeddingStore.query(embedding, 0.5, Restriction.unrestricted());
  }
}

