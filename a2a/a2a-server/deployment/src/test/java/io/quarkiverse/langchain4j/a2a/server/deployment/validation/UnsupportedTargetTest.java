package io.quarkiverse.langchain4j.a2a.server.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.inject.spi.DeploymentException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A plain interface is not an AI Service, so there is nothing to expose.
 */
public class UnsupportedTargetTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(NotAnAgent.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(DeploymentException.class)
                    .hasMessageContaining("@RegisterAiService"));

    @Test
    public void test() {
        fail("should never be called");
    }

    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "noop", name = "No op", description = "Does nothing", tags = "none"))
    public interface NotAnAgent {

        String chat(String question);
    }
}
