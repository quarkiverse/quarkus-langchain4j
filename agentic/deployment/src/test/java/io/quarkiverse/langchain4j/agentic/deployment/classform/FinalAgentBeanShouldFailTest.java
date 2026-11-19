package io.quarkiverse.langchain4j.agentic.deployment.classform;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.context.ApplicationScoped;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A final class with an instance agent method is a plain class, unless it declares a scope: then it is a bean, and
 * the build fails because a bean with an instance agent method is invoked through a client proxy.
 */
public class FinalAgentBeanShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(IllegalConfigurationException.class)
                    .hasMessageContaining("must not be final"));

    @Test
    public void test() {
        fail("should never be called");
    }

    @ApplicationScoped
    public static final class Greeter {
        @Agent(outputKey = "greeting")
        public String greet(@V("name") String name) {
            return "Hello " + name;
        }
    }
}
