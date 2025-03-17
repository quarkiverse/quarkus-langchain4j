package io.quarkiverse.langchain4j.sample;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import java.util.List;

@Entity
public class Movie extends PanacheEntity {

    public String link;
    public String title;
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
      return find("title like ?1", "%" + title + "%").list();
    }
}

