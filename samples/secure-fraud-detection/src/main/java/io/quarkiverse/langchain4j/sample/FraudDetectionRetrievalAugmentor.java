package io.quarkiverse.langchain4j.sample;

import java.util.function.Supplier;

import org.eclipse.microprofile.context.ManagedExecutor;

import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FraudDetectionRetrievalAugmentor implements Supplier<RetrievalAugmentor> {

    @Inject
    FraudDetectionContentRetriever contentRetriever;

    @Inject
    ManagedExecutor executor;
    
    @Override
    public RetrievalAugmentor get() {
        return DefaultRetrievalAugmentor.builder()
                .executor(executor)
                .contentRetriever(contentRetriever).build();
    }
}
