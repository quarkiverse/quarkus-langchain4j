package io.quarkiverse.langchain4j.agentic.deployment.classform;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

public class WorkflowMethodWithBodyShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(IllegalConfigurationException.class)
                    .hasMessageContaining("declare the method 'native'"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public static class Greeter {
        @Agent(outputKey = "greeting")
        public static String greet(@V("name") String name) {
            return "Hello " + name;
        }
    }

    public static class Greeting {
        @SequenceAgent(outputKey = "greeting", subAgents = Greeter.class)
        public String greet(@V("name") String name) {
            return null;
        }
    }
}
