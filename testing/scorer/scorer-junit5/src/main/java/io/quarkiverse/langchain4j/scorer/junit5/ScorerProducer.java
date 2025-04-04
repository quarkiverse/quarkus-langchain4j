package io.quarkiverse.langchain4j.scorer.junit5;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.context.ManagedExecutor;

import io.quarkiverse.langchain4j.testing.scorer.Scorer;

public class ScorerProducer {

    @ApplicationScoped
    public Scorer scorer(ManagedExecutor executor) {
        return new Scorer(executor);
    }
}
