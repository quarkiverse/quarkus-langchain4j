package io.quarkiverse.langchain4j.sample.chatbot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "movie", schema = "public")
public class Movie {

    @Id
    @GeneratedValue
    private int id;

    @Column(name = "index")
    private int index;

    @Column(name = "movie_name")
    @JsonProperty("movie_name")
    private String movieName;

    @Column(name = "year_of_release")
    @JsonProperty("year_of_release")
    private int yearOfRelease;

    @Column(name = "category")
    private String category;

    @Column(name = "run_time")
    @JsonProperty("run_time")
    private int runTime;

    @Column(name = "genre")
    private String genre;

    @Column(name = "imdb_rating")
    @JsonProperty("imdb_rating")
    private float imdbRating;

    @Column(name = "votes")
    private Integer votes;

    @Column(name = "gross_total")
    @JsonProperty("gross_total")
    private float grossTotal;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getMovieName() {
        return movieName;
    }

    public void setMovieName(String movieName) {
        this.movieName = movieName;
    }

    public int getYearOfRelease() {
        return yearOfRelease;
    }

    public void setYearOfRelease(int yearOfRelease) {
        this.yearOfRelease = yearOfRelease;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getRunTime() {
        return runTime;
    }

    public void setRunTime(int runTime) {
        this.runTime = runTime;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public float getImdbRating() {
        return imdbRating;
    }

    public void setImdbRating(float imdbRating) {
        this.imdbRating = imdbRating;
    }

    public Integer getVotes() {
        return votes;
    }

    public void setVotes(Integer votes) {
        this.votes = votes;
    }

    public float getGrossTotal() {
        return grossTotal;
    }

    public void setGrossTotal(float grossTotal) {
        this.grossTotal = grossTotal;
    }
}
