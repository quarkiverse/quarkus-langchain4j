package io.quarkiverse.langchain4j.samples.usersrag;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * In-memory stand-in for the store that holds the augmentation documents of each user.
 * The key is the principal name of the authenticated user.
 */
@ApplicationScoped
public class UserDocuments {

    private final Map<String, List<String>> documentsByUser = new ConcurrentHashMap<>();

    @PostConstruct
    void seed() {
        add("alice", "Alice's private document: the launch passcode is ALPHA-1.");
        add("bob", "Bob's private document: the launch passcode is BRAVO-2.");
    }

    public void add(String username, String document) {
        documentsByUser.putIfAbsent(username, new CopyOnWriteArrayList<>());
        documentsByUser.get(username).add(document);
    }

    public List<String> documents(String username) {
        return documentsByUser.getOrDefault(username, List.of());
    }
}
