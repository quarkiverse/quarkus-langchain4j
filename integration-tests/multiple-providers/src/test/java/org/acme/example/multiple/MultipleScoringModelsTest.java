package org.acme.example.multiple;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.watsonx.WatsonxScoringModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.cohere.runtime.QuarkusCohereScoringModel;
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

    @Inject
    @Any
    Instance<ScoringModel> anyModel;

    @Test
    void firstNamedModel() {
        assertThat(ClientProxy.unwrap(firstNamedModel)).isInstanceOf(WatsonxScoringModel.class);
    }

    @Test
    void secondNamedModel() {
        assertThat(ClientProxy.unwrap(secondNamedModel)).isInstanceOf(QuarkusCohereScoringModel.class);
    }

    @Test
    void anyInstanceInjection() {
        // s3 model has no direct injection point; we only request it via Instance<T>
        assertThat(anyModel.handlesStream()
                .anyMatch(handle -> handle.getBean().getQualifiers().contains(ModelName.Literal.of("s3")))).isTrue();
    }
}
