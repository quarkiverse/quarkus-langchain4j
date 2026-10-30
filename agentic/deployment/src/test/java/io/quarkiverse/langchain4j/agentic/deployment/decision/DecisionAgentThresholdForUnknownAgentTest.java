package io.quarkiverse.langchain4j.agentic.deployment.decision;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionAgentThresholdForUnknownAgentTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class))
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.agentic.decision-agent.spamGat.threshold", "0.7")
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
            .assertLogRecords(DecisionAgentThresholdForUnknownAgentTest::assertWarnsAboutUnknownAgent);

    public interface SpamGate {
        @Decide("Is this message spam?")
        @Agent(name = "spamGate", description = "Detects spam", outputKey = "spam")
        boolean isSpam(@V("message") String message);
    }

    @Inject
    SpamGate spamGate;

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new FixedDecisionModel("fixed");
        }
    }

    private static void assertWarnsAboutUnknownAgent(List<LogRecord> records) {
        Assertions.assertThat(records).anySatisfy(record -> Assertions.assertThat(record.getMessage())
                .contains("there is no decision agent named"));
    }

    @Test
    void test() {
    }
}
