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
 * Every request is served by the one agent there is, so two would leave the server with no way of telling which
 * should answer.
 */
public class TwoAgentsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(FirstAgent.class, SecondAgent.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(DeploymentException.class)
                    .hasMessageContaining(FirstAgent.class.getName())
                    .hasMessageContaining(SecondAgent.class.getName())
                    .hasMessageContaining("an application can only expose one agent"));

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
