package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.WEATHER_QUESTION;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.cancelTask;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.post;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A client that waits for the answer receives the task once it is already terminal, so cancelling it afterwards is
 * refused. The SDK answers that from the task store, before the executor is consulted, and these tests pin the
 * answers a client receives.
 */
public class CancelTaskTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, TestAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent")
            .overrideRuntimeConfigKey("a2a.authorization.required", "false");

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testACompletedTaskCannotBeCancelled() {
        setChatCompletionMessageContent("It is sunny in LA.");
        String taskId = sendMessage(WEATHER_QUESTION).post().extract().path("result.task.id");

        post(cancelTask(taskId))
                .body("error.data[0].reason", equalTo("TASK_NOT_CANCELABLE"));
    }

    /**
     * A task that was never created cannot be confused with one that cannot be cancelled: the client has to be able
     * to tell "you sent the wrong id" apart from "that one is already finished".
     */
    @Test
    public void testCancellingAnUnknownTaskIsReportedAsNotFound() {
        post(cancelTask("no-such-task"))
                .body("error.data[0].reason", equalTo("TASK_NOT_FOUND"));
    }
}
