package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import io.quarkiverse.langchain4j.ollama.DisabledDecisionModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class OllamaDecisionModelEnableIntegrationFalseTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.enable-integration", "false");

    private static final DecisionRequest REQUEST = DecisionRequest.builder()
            .input("Congratulations! You won a free cruise!")
            .question("spam", YesNoQuestion.from("Is this message spam?"))
            .build();

    @Inject
    DecisionModel model;

    @Test
    void blockingCallFailsWithModelDisabled() {
        assertThat(ClientProxy.unwrap(model)).isInstanceOf(DisabledDecisionModel.class);

        assertThatThrownBy(() -> model.decide(REQUEST)).isInstanceOf(ModelDisabledException.class);
    }

    @Test
    void asyncCallFailsWithModelDisabled() {
        assertThatThrownBy(() -> model.decideAsync(REQUEST).get())
                .hasRootCauseInstanceOf(ModelDisabledException.class);
    }
}
