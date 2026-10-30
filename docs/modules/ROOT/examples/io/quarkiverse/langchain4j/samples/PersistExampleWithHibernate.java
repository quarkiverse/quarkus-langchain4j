package io.quarkiverse.langchain4j.samples;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.hibernate.Session;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

@ApplicationScoped
public class PersistExampleWithHibernate {

    /**
     * The Hibernate Session (the database).
     * The bean is provided by the quarkus-hibernate extension.
     */
    @Inject
    Session session;

    /**
     * The embedding model (how is computed the vector of a document).
     * The bean is provided by the LLM (like openai) extension.
     */
    @Inject
    EmbeddingModel embeddingModel;

    @Transactional
    public void ingest(List<MyEmbeddingEntity> entities) {
        List<TextSegment> textSegments = new ArrayList<>(entities.size());
        for (MyEmbeddingEntity entity : entities) {
            textSegments.add(TextSegment.from(entity.text));
        }
        List<Embedding> embeddings = embeddingModel.embedAll(textSegments).content();
        for (int i = 0; i < entities.size(); i++) {
            MyEmbeddingEntity entity = entities.get(i);
            entity.embedding = embeddings.get(i).vector();
            session.persist(entity);
        }
    }
}
