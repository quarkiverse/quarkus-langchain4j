package io.quarkiverse.langchain4j.sample.chatbot;

import java.util.List;
import java.util.Random;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class Setup {

    public static List<String> GENRES = List.of("Crime", "Drama", "Thriller", "Action", "Adventure", "Fantasy",
    		"Horror", "Romance", "Sci-Fi", "Biography", "History", "Mystery", "War", "Animation",
    		"Comedy");
    
    static String getARandomGenre() {
        return GENRES.get(new Random().nextInt(GENRES.size()));
    }

    @Inject
    MovieWatcherConfig config;

    @Transactional
    public void init(@Observes StartupEvent ev, MovieWatcherRepository movieWatchers) {
        movieWatchers.deleteAll();

        var movieWatcher = new MovieWatcher();
        movieWatcher.name = config.name();
        movieWatcher.email = config.email();
        movieWatcher.preferredGenre = getARandomGenre();
        movieWatchers.persist(movieWatcher);

        Log.infof("Movie watcher name: %s, email: %s, preferred genre: %s", movieWatcher.name, movieWatcher.email, movieWatcher.preferredGenre);
    }
}
