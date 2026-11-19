package io.quarkiverse.langchain4j.agentic.deployment.classform;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A private instance helper is rejected at build time: the client proxy of the bean cannot override it, so
 * langchain4j-agentic would reject it on the first invocation of the agent, when its agent object is created.
 */
public class PrivateInstanceHelperShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(IllegalConfigurationException.class)
                    .hasMessageContaining("can be neither private nor final")
                    .hasMessageContaining("Offending method is 'reached'"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public static class Counter {
        @Agent(outputKey = "count")
        public static int increment(@V("count") int count) {
            return count + 1;
        }
    }

    public static class CountingLoop {
        @LoopAgent(outputKey = "count", maxIterations = 10, subAgents = Counter.class)
        public native int count(@V("count") int count);

        @ExitCondition
        private boolean reached(@V("count") int count) {
            return count >= 3;
        }
    }
}
