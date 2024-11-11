package io.quarkiverse.langchain4j.sample.chatbot;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class MovieWatcher {

    @Id
    @GeneratedValue
    public Long id;
    public String name;
    public String email;
    public String preferredGenre;
}
