package io.quarkiverse.langchain4j.test.decision;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.decision.Decide;
import io.quarkiverse.langchain4j.RegisterDecisionService;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionServiceThresholdWithoutBooleanAnswerTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(FixedDecisionModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.decision-service.Router.threshold", "0.7")
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("quarkus.langchain4j.decision-service.\"Router\"")
                    .hasStackTraceContaining("has no boolean answer"));

    public enum Team {
        BILLING,
        SUPPORT
    }

    @RegisterDecisionService
    public interface Router {
        @Decide("Which team should handle this request?")
        Team route(String request);
    }

    public static class Models {
        @Produces
        @ApplicationScoped
        DecisionModel decisionModel() {
            return new FixedDecisionModel("fixed");
        }
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}
