package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.WEATHER_QUESTION;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.getTask;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.post;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import jakarta.enterprise.context.ApplicationScoped;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A provider of the application's own replaces the one that denies everything, and every task operation is then its
 * to allow, here one that lets anyone send but no one read a task back. The SDK consults a provider whenever there is
 * one, so it applies even with {@code a2a.authorization.required} off.
 */
public class TaskAuthorizationProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, FakeChatModel.class, SunnyChatModel.class, TestAgent.class,
                    WriteOnlyAuthorizationProvider.class))
            .overrideRuntimeConfigKey("a2a.authorization.required", "false");

    @RegisterAiService(chatLanguageModelSupplier = SunnyChatModel.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @ApplicationScoped
    public static class WriteOnlyAuthorizationProvider implements TaskAuthorizationProvider {

        @Override
        public boolean checkRead(ServerCallContext context, String taskId, TaskOperation operation) {
            return false;
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

    @Test
    public void testTheProviderDecidesWhoReadsATask() {
        String taskId = sendMessage(WEATHER_QUESTION).post()
                .body("error", nullValue())
                .body("result.task.artifacts[0].parts[0].text", equalTo("It is sunny."))
                .extract().path("result.task.id");

        post(getTask(taskId))
                .body("result", nullValue())
                .body("error.data[0].reason", equalTo("TASK_NOT_FOUND"));
    }
}
