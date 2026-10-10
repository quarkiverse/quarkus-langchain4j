package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * The generated executor calls the AI Service and emits the artifact only once that call returns, so an exception
 * escapes before any artifact is added. It leaves the task in the {@code FAILED} state the specification defines
 * for work that could not be completed, rather than reaching the client as a transport error that says nothing
 * about the task. The message on it does not repeat the exception, whose text is for the server log.
 *
 * @see <a href="https://a2a-protocol.org/latest/specification/#413-taskstate">A2A spec §4.1.3 — TaskState</a>
 */
public class FailingAgentTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class, ThrowingChatModelSupplier.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent");

    /**
     * Supplying the model keeps the failure the agent's own, with no provider or network in the way.
     */
    public static class ThrowingChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    throw new IllegalStateException("the model is unreachable");
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = ThrowingChatModelSupplier.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testAnAgentThatThrowsLeavesTheTaskFailed() {
        given()
                .contentType("application/json")
                .header("A2A-Version", "1.0")
                .body("""
                        {
                          "jsonrpc": "2.0",
                          "id": "1",
                          "method": "SendMessage",
                          "params": {
                            "message": {
                              "messageId": "message-1",
                              "contextId": "context-1",
                              "role": "ROLE_USER",
                              "parts": [{ "text": "what is the weather in LA?" }]
                            },
                            "configuration": { "returnImmediately": false }
                          }
                        }""")
                .when().post("/")
                .then()
                .statusCode(200)
                // a task, not a transport error: the client can tell a failed agent from a broken server
                .body("error", nullValue())
                .body("result.task.status.state", equalTo("TASK_STATE_FAILED"))
                .body("result.task.status.message.parts[0].text", equalTo("The agent failed to answer."))
                .body("result.task.status.message.parts[0].text", not(containsString("the model is unreachable")));
    }
}
