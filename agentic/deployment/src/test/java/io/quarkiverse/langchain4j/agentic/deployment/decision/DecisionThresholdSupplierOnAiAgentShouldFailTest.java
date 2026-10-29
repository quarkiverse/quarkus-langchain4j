package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.DecisionThresholdSupplier;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.ThresholdContext;
import io.quarkus.test.QuarkusExtensionTest;

public class DecisionThresholdSupplierOnAiAgentShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining(
                            "declares a method annotated with @DecisionThresholdSupplier, but it is not a decision agent"));

    public interface Writer {
        @UserMessage("Write a story about {{topic}}")
        @Agent(description = "Writes a story", outputKey = "story")
        String write(@V("topic") String topic);

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
