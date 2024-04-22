package io.quarkiverse.langchain4j.test.devui;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ModelName;

@Singleton
public class Dummy {

    @Inject
    @ModelName("model1")
    EmbeddingModel embeddingModel1;

}
