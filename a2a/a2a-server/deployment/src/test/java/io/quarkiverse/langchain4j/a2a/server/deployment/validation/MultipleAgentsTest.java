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
 * An A2A server is discovered through a single Agent Card, so an application that exposes two agents has no way of
 * saying which one the card describes.
 */
public class MultipleAgentsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(FirstAgent.class, SecondAgent.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(DeploymentException.class)
                    .hasMessageContaining("only exposing a single A2A agent is supported"));

    @Test
    public void test() {
        fail("should never be called");
    }

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "first", name = "First", description = "The first", tags = "one"))
    public interface FirstAgent {

        String chat(@UserMessage String question);
    }

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "second", name = "Second", description = "The second", tags = "two"))
    public interface SecondAgent {

        String chat(@UserMessage String question);
    }
}
