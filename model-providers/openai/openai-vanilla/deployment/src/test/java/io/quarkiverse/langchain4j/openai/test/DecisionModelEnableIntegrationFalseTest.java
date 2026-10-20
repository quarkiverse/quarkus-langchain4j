package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.openai.DisabledDecisionModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionModelEnableIntegrationFalseTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.enable-integration", "false");

    private static final DecisionRequest REQUEST = DecisionRequest.builder()
            .input("Congratulations! You won a free cruise!")
            .question("spam", YesNoQuestion.of("Is this message spam?"))
            .build();

    @Inject
    DecisionModel model;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void blockingCallFailsWithModelDisabled() {
        assertThat(ClientProxy.unwrap(model)).isInstanceOf(DisabledDecisionModel.class);

        assertThatThrownBy(() -> model.decide(REQUEST)).isInstanceOf(ModelDisabledException.class);

        assertThat(mock.requests()).isEmpty();
    }

    @Test
    void asyncCallFailsWithModelDisabled() {
        assertThatThrownBy(() -> model.decideAsync(REQUEST).get())
                .hasRootCauseInstanceOf(ModelDisabledException.class);
    }
}
