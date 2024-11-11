package io.quarkiverse.langchain4j.sample.chatbot;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MovieWatcherRepository implements PanacheRepository<MovieWatcher> {
	
	public String findPreferredGenre(String movieWatcherName, String movieWatcherEmail) {
		MovieWatcher movieWatcher = find("name = ?1 and email = ?2", movieWatcherName, movieWatcherEmail).firstResult();
        if (movieWatcher == null) {
            throw new MissingMovieWatcherException();
        }
        return movieWatcher.preferredGenre;
    }
}
