package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
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
 * answers a client receives. Cancelling a task the agent is still working on is covered by
 * {@link CancelRunningTaskTest}.
 */
public class CancelTaskTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent");

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testACompletedTaskCannotBeCancelled() {
        setChatCompletionMessageContent("It is sunny in LA.");
        String taskId = sendMessageAndExtractTaskId();

        given()
                .contentType("application/json")
                .header("A2A-Version", "1.0")
                .body("{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"CancelTask\",\"params\":{\"id\":\"" + taskId
                        + "\"}}")
                .when().post("/")
                .then()
                .statusCode(200)
                .body("error.data[0].reason", equalTo("TASK_NOT_CANCELABLE"));
    }

    /**
     * A task that was never created cannot be confused with one that cannot be cancelled: the client has to be able
     * to tell "you sent the wrong id" apart from "that one is already finished".
     */
    @Test
    public void testCancellingAnUnknownTaskIsReportedAsNotFound() {
        given()
                .contentType("application/json")
                .header("A2A-Version", "1.0")
                .body("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"CancelTask\",\"params\":{\"id\":\"no-such-task\"}}")
                .when().post("/")
                .then()
                .statusCode(200)
                .body("error.data[0].reason", equalTo("TASK_NOT_FOUND"));
    }

    private static String sendMessageAndExtractTaskId() {
        return given()
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
                .extract().path("result.task.id");
    }
}
