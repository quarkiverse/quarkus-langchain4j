package io.quarkiverse.langchain4j.a2a.server.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.UUID;

import jakarta.enterprise.inject.spi.DeploymentException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An A2A request carries text, so every parameter the generated executor fills has to be a {@code String} —
 * including the memory id, which is taken from the request's context id.
 */
public class NonStringParameterTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(TypedMemoryIdAgent.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(DeploymentException.class)
                    .hasMessageContaining("can only supply Strings"));

    @Test
    public void test() {
        fail("should never be called");
    }

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "noop", name = "No op", description = "Does nothing", tags = "none"))
    public interface TypedMemoryIdAgent {

        String chat(@MemoryId UUID memoryId, @UserMessage String question);
    }
}
