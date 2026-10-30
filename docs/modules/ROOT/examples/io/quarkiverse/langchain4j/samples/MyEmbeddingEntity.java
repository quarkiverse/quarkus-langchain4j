package io.quarkiverse.langchain4j.samples;

import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.Array;

import dev.langchain4j.store.embedding.hibernate.EmbeddedText;
import dev.langchain4j.store.embedding.hibernate.EmbeddingVector;
import dev.langchain4j.store.embedding.hibernate.MetadataAttribute;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;

@Entity
public class MyEmbeddingEntity {
    @Id
    UUID id;
    @EmbeddingVector
    @Array(length = 384) // The dimension of the embedding vector based on the embedding model
    float[] embedding;
    @EmbeddedText
    String text;
    @UnmappedMetadata
    Map<String, Object> metadata; // Can be either a Map<String, Object> or a String

    @MetadataAttribute
    String mimeType; // Explicitly mapped. Synchronizes TextSegment#metadata with this attribute
    @MetadataAttribute
    String fileName; // Explicitly mapped. Synchronizes TextSegment#metadata with this attribute
}
