package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An AI Service can return nothing at all, and an A2A artifact cannot carry zero parts. Completing the task in that
 * case would raise an SDK invariant and reach the client as an internal error, saying nothing about the agent, so
 * the task fails with a message that does.
 *
 * @see <a href="https://a2a-protocol.org/latest/specification/#413-taskstate">A2A spec §4.1.3 — TaskState</a>
 */
public class EmptyResultTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class, Weather.class, NullAnsweringChatModelSupplier.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent");

    /**
     * A model that answers with the JSON literal {@code null}, which is what a structured result deserializes from
     * when the model declines to answer.
     */
    public static class NullAnsweringChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder().aiMessage(new AiMessage("null")).build();
                }
            };
        }
    }

    public record Weather(String summary) {
    }

    @RegisterAiService(chatLanguageModelSupplier = NullAnsweringChatModelSupplier.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        Weather chat(@UserMessage String question);
    }

    @Test
    public void testAnAgentThatAnswersNothingLeavesTheTaskFailed() {
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
                // a task, not a transport error: the client can tell an empty-handed agent from a broken server
                .body("error", nullValue())
                .body("result.task.status.state", equalTo("TASK_STATE_FAILED"))
                .body("result.task.status.message.parts[0].text", containsString("produced no answer"));
    }
}
