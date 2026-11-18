package io.quarkiverse.langchain4j.a2a.server.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.inject.spi.DeploymentException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The generated executor reaches the agent through an interface call, so a class carrying the annotation has to be
 * refused at build time rather than fail with an {@code IncompatibleClassChangeError} on the first request.
 */
public class NonInterfaceTargetTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(AgentClass.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(DeploymentException.class)
                    .hasMessageContaining("can only be placed on an interface"));

    @Test
    public void test() {
        fail("should never be called");
    }

    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "noop", name = "No op", description = "Does nothing", tags = "none"))
    public static class AgentClass {

        public String chat(String question) {
            return question;
        }
    }
}
