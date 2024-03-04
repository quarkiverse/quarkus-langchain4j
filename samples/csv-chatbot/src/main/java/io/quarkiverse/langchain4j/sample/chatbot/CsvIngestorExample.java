package io.quarkiverse.langchain4j.sample.chatbot;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.StatelessSession;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class CsvIngestorExample {

    @ConfigProperty(name = "csv.file")
    File file;

    @ConfigProperty(name = "csv.headers")
    List<String> headers;

    @Inject
    StatelessSession session;

    @Transactional
    public void ingest(@Observes StartupEvent event) throws IOException {
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(headers.toArray(new String[0]))
                .setSkipHeaderRecord(true)
                .build();
        int count = 0;
        try (Reader reader = new FileReader(file)) {
            Iterable<CSVRecord> records = csvFormat.parse(reader);
            for (CSVRecord record : records) {
                Movie movie = new Movie();
                movie.setIndex(Integer.parseInt(record.get("index")));
                movie.setMovieName(record.get("movie_name"));
                movie.setYearOfRelease(Integer.parseInt(record.get("year_of_release")));
                movie.setCategory(record.get("category"));
                movie.setRunTime(Integer.parseInt(record.get("run_time")));
                movie.setGenre(record.get("genre"));
                movie.setImdbRating(Float.parseFloat(record.get("imdb_rating")));
                movie.setVotes(
                        Integer.parseInt(record.get("votes").substring(1, record.get("votes").length() - 1).replace(",", "")));
                movie.setGrossTotal(Float.parseFloat(record.get("gross_total")));
                session.insert(movie);
                count++;
            }
            Log.infof("Ingested %d movies.%n", count);
        }

    }
}
