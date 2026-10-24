package io.quarkiverse.langchain4j.typesafe.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.DecisionReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class TypeSafeNamedModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset("quarkus.langchain4j.typesafe.t1.decision-model.model-name=jev-named\n"
                                    + "quarkus.langchain4j.typesafe.t1.api-key=my-key\n"),
                            "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.t1.decision-model.provider", "typesafe");

    @Inject
    @ModelName("t1")
    DecisionModel model;

    @Inject
    MockModelServer mock;

    @Test
    void namedModelReachesTheMock() {
        mock.reset();
        mock.decision().reply(DecisionReply.of(DecisionReply.predicate("urgent", 0.2)));

        assertThat(model.decide(DecisionRequest.builder()
                .input("hi")
                .question("urgent", YesNoQuestion.builder().text("Urgent?").build())
                .build()).yesNo("urgent").probability()).isEqualTo(0.2);

        CapturedRequest r = mock.decision().singleRequest();
        assertThat(r.path()).isEqualTo("/typesafe/v1/systemone");
        assertThat(r.asDecision().modelName()).isEqualTo("jev-named");
        assertThat(r.asDecision().inputText()).isEqualTo("hi");
        // a configured api-key is sent as is
        assertThat(r.header("Authorization")).isEqualTo("Bearer my-key");
    }
}
