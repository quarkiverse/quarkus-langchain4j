package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.service.IllegalConfigurationException;
import io.quarkiverse.langchain4j.agentic.deployment.Agents;
import io.quarkus.test.QuarkusUnitTest;

public class NonAgentListenerReturnTypeAgentListenerSupplierTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(Agents.class, StyleReviewLoopAgentWithListener.class))
            .assertException(
                    throwable -> Assertions.assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("AgentListener"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public interface StyleReviewLoopAgentWithListener extends Agents.StyleReviewLoopAgent {

        @AgentListenerSupplier
        static int listener() {
            return 1;
        }
    }
}
