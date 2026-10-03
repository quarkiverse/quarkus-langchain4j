package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.WEATHER_QUESTION;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An AI Service can return nothing at all, and an A2A artifact cannot carry zero parts. Completing the task in that
 * case would raise an SDK invariant and reach the client as an internal error, saying nothing about the agent, so
 * the task fails with a message that does.
 *
 * @see <a href="https://a2a-protocol.org/latest/specification/#413-taskstate">A2A spec §4.1.3: TaskState</a>
 */
public class EmptyResultTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, TestAgent.class, Weather.class, FakeChatModel.class, NullAnsweringChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent")
            .overrideRuntimeConfigKey("a2a.authorization.required", "false");

    /**
     * A model that answers with the JSON literal {@code null}, which is what a structured result deserializes from
     * when the model declines to answer.
     */
    public static class NullAnsweringChatModel extends FakeChatModel {

        @Override
        protected String answer(ChatRequest request) {
            return "null";
        }
    }

    public record Weather(String summary) {
    }

    @RegisterAiService(chatLanguageModelSupplier = NullAnsweringChatModel.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        Weather chat(@UserMessage String question);
    }

    @Test
    public void testAnAgentThatAnswersNothingLeavesTheTaskFailed() {
        sendMessage(WEATHER_QUESTION).post()
                // a task, not a transport error: the client can tell an empty-handed agent from a broken server
                .body("error", nullValue())
                .body("result.task.status.state", equalTo("TASK_STATE_FAILED"))
                .body("result.task.status.message.parts[0].text", containsString("produced no answer"));
    }
}
