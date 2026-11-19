package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.service.V;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that a class with an instance agent method that cannot be a bean, because it is final, a record or has no
 * no-arg constructor, stays a plain class, as in plain LangChain4j: the build succeeds and the application passes its
 * instances to a programmatic builder.
 */
public class PlainInstanceAgentClassesTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    public static final class Stamp {

        @Agent(name = "stamp", description = "Stamps a text", outputKey = "stamped")
        public String stamp(@V("text") String text) {
            return "[" + text + "]";
        }
    }

    public record Prefixer(String prefix) {

        @Agent(name = "prefixer", description = "Prefixes a text", outputKey = "prefixed")
        public String apply(@V("stamped") String text) {
            return prefix + text;
        }
    }

    public static class Suffixer {

        private final String suffix;

        public Suffixer(String suffix) {
            this.suffix = suffix;
        }

        @Agent(name = "suffixer", description = "Suffixes a text", outputKey = "result")
        public String apply(@V("prefixed") String text) {
            return text + suffix;
        }
    }

    @Test
    void theClassesAreNotBeans() {
        assertThat(Arc.container().instance(Stamp.class).isAvailable()).isFalse();
        assertThat(Arc.container().instance(Prefixer.class).isAvailable()).isFalse();
        assertThat(Arc.container().instance(Suffixer.class).isAvailable()).isFalse();
    }

    @Test
    void instancesArePassedToAProgrammaticBuilder() {
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(new Stamp(), new Prefixer(">> "), new Suffixer(" <<"))
                .outputKey("result")
                .build();
        assertThat(workflow.invoke(Map.of("text", "hello"))).isEqualTo(">> [hello] <<");
    }
}
