package io.quarkiverse.langchain4j.a2a.server.deployment;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The SDK reads {@code FALSE} as authorization off, while the provider that denies everything only steps aside for
 * {@code false}, so every request would be denied by a check the application believes is off. Startup fails instead,
 * saying which value to use.
 */
public class AuthorizationOffInUppercaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(FakeChatModel.class, SunnyChatModel.class, TestAgent.class))
            .overrideRuntimeConfigKey("a2a.authorization.required", "FALSE")
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .hasStackTraceContaining("'a2a.authorization.required' is set to 'FALSE'")
                    .hasStackTraceContaining("Set it to 'false'"));

    @RegisterAiService(chatLanguageModelSupplier = SunnyChatModel.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void test() {
        fail("should never be called");
    }
}
