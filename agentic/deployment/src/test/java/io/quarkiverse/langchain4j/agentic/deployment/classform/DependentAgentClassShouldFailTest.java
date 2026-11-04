package io.quarkiverse.langchain4j.agentic.deployment.classform;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.context.Dependent;

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

public class DependentAgentClassShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(IllegalConfigurationException.class)
                    .hasMessageContaining("must not be @Dependent"));

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

    @Dependent
    public static class DependentGreeting {
        @SequenceAgent(outputKey = "greeting", subAgents = Greeter.class)
        public native String greet(@V("name") String name);
    }
}
