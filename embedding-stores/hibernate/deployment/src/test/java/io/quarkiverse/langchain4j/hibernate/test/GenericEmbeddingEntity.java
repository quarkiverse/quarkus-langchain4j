package io.quarkiverse.langchain4j.hibernate.test;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Array;

import dev.langchain4j.store.embedding.hibernate.EmbeddedText;
import dev.langchain4j.store.embedding.hibernate.Embedding;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;

@Entity
@Table(name = "generic_embedding_entity")
public class GenericEmbeddingEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @EmbeddedText
    private String text;

    @Embedding
    @Array(length = 384)
    private float[] embedding;

    @UnmappedMetadata
    private String metadata;
}
