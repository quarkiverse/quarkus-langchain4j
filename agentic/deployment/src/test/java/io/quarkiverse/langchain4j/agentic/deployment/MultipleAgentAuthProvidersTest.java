package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.agentic.auth.AgentAuthProvider;
import io.quarkus.test.QuarkusExtensionTest;

public class MultipleAgentAuthProvidersTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(First.class, Second.class))
            .assertException(e -> assertThat(e).hasStackTraceContaining(
                    "Only one AgentAuthProvider bean is supported, found 2: "
                            + First.class.getName() + ", " + Second.class.getName()));

    @ApplicationScoped
    public static class First implements AgentAuthProvider {
        @Override
        public String getAuthorization(Input input) {
            return "Bearer first";
        }
    }

    @ApplicationScoped
    public static class Second implements AgentAuthProvider {
        @Override
        public String getAuthorization(Input input) {
            return "Bearer second";
        }
    }

    @Test
    void buildFails() {
        fail("The build should have failed");
    }
}
