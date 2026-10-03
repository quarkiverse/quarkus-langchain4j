package io.quarkiverse.langchain4j.testing.a2aserver;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;

/**
 * Exercises the HTTP+JSON transport, which an application opts into by putting the REST reference implementation on
 * the classpath. Where JSON-RPC tunnels every operation through a single POST on the root path, this binding gives
 * each one its own path and method.
 */
@QuarkusTest
public class RestTransportTest {

    @Test
    public void testSendMessageIsAnsweredByTheAgent() {
        restRequest()
                .body("""
                        {
                          "message": {
                            "messageId": "message-1",
                            "contextId": "context-1",
                            "role": "ROLE_USER",
                            "parts": [{ "text": "weather in LA?" }]
                          },
                          "configuration": { "returnImmediately": false }
                        }""")
                .when().post("/message:send")
                .then()
                .statusCode(200)
                .body("task.artifacts[0].parts[0].text", equalTo("You asked: weather in LA?"))
                .body("task.status.state", equalTo("TASK_STATE_COMPLETED"));
    }

    @Test
    public void testTaskCanBeFetchedBackAfterTheMessageWasAnswered() {
        String taskId = restRequest()
                .body("""
                        {
                          "message": {
                            "messageId": "message-2",
                            "contextId": "context-2",
                            "role": "ROLE_USER",
                            "parts": [{ "text": "weather in NY?" }]
                          },
                          "configuration": { "returnImmediately": false }
                        }""")
                .when().post("/message:send")
                .then()
                .statusCode(200)
                .body("task.id", notNullValue())
                .extract().path("task.id");

        restRequest()
                .when().get("/tasks/" + taskId)
                .then()
                .statusCode(200)
                .body("id", equalTo(taskId))
                .body("artifacts[0].parts[0].text", equalTo("You asked: weather in NY?"));
    }

    /**
     * The binding names its custom methods {@code message:send} and {@code tasks/{id}:cancel}, and the colon has to
     * reach the server as itself rather than percent-encoded for those routes to match.
     */
    private static RequestSpecification restRequest() {
        return given()
                .urlEncodingEnabled(false)
                .contentType("application/json")
                .header("A2A-Version", "1.0");
    }
}
