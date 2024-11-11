package io.quarkiverse.langchain4j.sample.chatbot;

import java.io.File;
import java.io.IOException;
import java.util.List;

import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;

public class CsvIngestorExample {

    private final File file;
    private final StatelessSession session;

    public CsvIngestorExample(@ConfigProperty(name = "csv.file") File file,
            StatelessSession session) {
        this.file = file;
        this.session = session;
    }

    public void ingest(@Observes StartupEvent event) throws IOException {
        try (MappingIterator<Movie> it = new CsvMapper()
                .readerFor(Movie.class)
                .with(CsvSchema.emptySchema().withHeader())
                .readValues(file)) {
            List<Movie> movies = it.readAll();
            Transaction transaction = session.beginTransaction();
            for (Movie movie : movies) {
                session.insert(movie);
            }
            transaction.commit();
            Log.infof("Ingested %d movies.%n", movies.size());
        }
    }
}
