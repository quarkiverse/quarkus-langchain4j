package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.store.embedding.hibernate.EmbeddedText;
import dev.langchain4j.store.embedding.hibernate.Embedding;
import dev.langchain4j.store.embedding.hibernate.MetadataAttribute;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Array;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "Movie")
public class Movie extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public Long id;

    @Embedding
    @Array(length = 384)                // The dimension of the embedding vector based on the embedding model
    public float[] embedding;

    @MetadataAttribute
    public String link;

    @UnmappedMetadata
    public String unmappedMetadata;

    @MetadataAttribute
    public String title;

    @EmbeddedText
    public String overview;

    public static Movie fromCsvLine(String line) {
       String values [] = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
       Movie movie = new Movie();
       movie.link = values[0].replace("\"", "");
       movie.title = values[1].replace("\"", "");
       movie.overview = values[7].replace("\"", "");
       if (movie.overview.length() > 255) {
           movie.overview = movie.overview.substring(0, 255);
       }
       return movie;
    }

    public static List<Movie> searchByTitleLike(String title) {
        return find("lower(title) like ?1", "%" + title.toLowerCase() + "%").list();
    }
}

