package io.quarkiverse.langchain4j.sample.chatbot;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "movie.watcher")
public interface MovieWatcherConfig {

    String name();

    String email();
}
