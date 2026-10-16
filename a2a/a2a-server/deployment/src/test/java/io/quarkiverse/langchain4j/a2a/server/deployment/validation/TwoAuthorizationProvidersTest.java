package io.quarkiverse.langchain4j.a2a.server.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.DeploymentException;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.a2a.server.deployment.FakeChatModel;
import io.quarkiverse.langchain4j.a2a.server.deployment.SunnyChatModel;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The SDK only uses a provider it can resolve unambiguously, so two of the application's own would both be ignored,
 * and the task operations decided without either.
 */
public class TwoAuthorizationProvidersTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(TestAgent.class, FakeChatModel.class, SunnyChatModel.class,
                    FirstProvider.class, SecondProvider.class))
            .assertException(throwable -> Assertions.assertThat(throwable)
                    .isInstanceOf(DeploymentException.class)
                    .hasMessageContaining("more than one TaskAuthorizationProvider bean")
                    .hasMessageContaining(FirstProvider.class.getName())
                    .hasMessageContaining(SecondProvider.class.getName()));

    @Test
    public void test() {
        fail("should never be called");
    }

    @RegisterAiService(chatLanguageModelSupplier = SunnyChatModel.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    public abstract static class AllowingProvider implements TaskAuthorizationProvider {

        @Override
        public boolean checkRead(ServerCallContext context, String taskId, TaskOperation operation) {
            return true;
        }

        @Override
        public boolean checkWrite(ServerCallContext context, String taskId, TaskOperation operation) {
            return true;
        }

        @Override
        public boolean checkCreate(ServerCallContext context, TaskOperation operation) {
            return true;
        }

        @Override
        public boolean isTaskRecorded(String taskId) {
            return true;
        }

        @Override
        public void recordOwnership(ServerCallContext context, String taskId, TaskOperation operation) {
        }
    }

    @ApplicationScoped
    public static class FirstProvider extends AllowingProvider {
    }

    @ApplicationScoped
    public static class SecondProvider extends AllowingProvider {
    }
}
