package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.DecisionThresholdSupplier;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.ThresholdContext;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionThresholdSupplierWithoutBooleanAnswerShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("declares a @DecisionThresholdSupplier method, but it has no boolean answer"));

    public enum Team {
        BILLING,
        SUPPORT
    }

    public interface Router {
        @Decide("Which team should handle this request?")
        @Agent(description = "Routes a request", outputKey = "team")
        Team route(@V("request") String request);

        @DecisionThresholdSupplier
        static double threshold(ThresholdContext context) {
            return 0.5;
        }
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}
