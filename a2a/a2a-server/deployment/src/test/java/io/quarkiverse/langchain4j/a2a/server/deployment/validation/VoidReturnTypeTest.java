package io.quarkiverse.langchain4j.a2a.server.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.inject.spi.DeploymentException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The return value of the exposed method is what reaches the client as an artifact, so a method that returns
 * nothing has nothing to serve.
 */
public class VoidReturnTypeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(VoidAgent.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(DeploymentException.class)
                    .hasMessageContaining("returns void"));

    @Test
    public void test() {
        fail("should never be called");
    }

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "noop", name = "No op", description = "Does nothing", tags = "none"))
    public interface VoidAgent {

        void chat(@UserMessage String question);
    }
}
