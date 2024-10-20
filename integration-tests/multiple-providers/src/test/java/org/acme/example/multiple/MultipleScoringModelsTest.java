package org.acme.example.multiple;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.cohere.runtime.QuarkusCohereScoringModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxScoringModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MultipleScoringModelsTest {

    @Inject
    @ModelName("s1")
    ScoringModel firstNamedModel;

    @Inject
    @ModelName("s2")
    ScoringModel secondNamedModel;

    @Test
    void firstNamedModel() {
        assertThat(ClientProxy.unwrap(firstNamedModel)).isInstanceOf(WatsonxScoringModel.class);
    }

    @Test
    void secondNamedModel() {
        assertThat(ClientProxy.unwrap(secondNamedModel)).isInstanceOf(QuarkusCohereScoringModel.class);
    }
}
