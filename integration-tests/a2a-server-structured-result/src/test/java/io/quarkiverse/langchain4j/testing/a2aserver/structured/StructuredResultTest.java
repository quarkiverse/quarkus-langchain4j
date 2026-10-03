package io.quarkiverse.langchain4j.testing.a2aserver.structured;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * A structured result is parsed from the model's answer into the method's return type, then written to the client as
 * JSON by the application's {@code ObjectMapper}, and both directions reach every type the result is built out of
 * reflectively. In a native image that only works for types the build registered, which is why these assertions
 * reach past the top level of the record.
 */
@QuarkusTest
public class StructuredResultTest {

    @Test
    public void testTheWholeResultGraphSurvivesSerialization() {
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
                              "parts": [{ "text": "forecast for LA" }]
                            },
                            "configuration": { "returnImmediately": false }
                          }
                        }""")
                .when().post("/")
                .then()
                .statusCode(200)
                .body("error", nullValue())
                .body("result.task.artifacts[0].parts[0].data.city", equalTo("LA"))
                // reachable only through a field of the result
                .body("result.task.artifacts[0].parts[0].data.at.latitude", equalTo(34.05f))
                // reachable only through the type argument of a List
                .body("result.task.artifacts[0].parts[0].data.days", hasSize(2))
                .body("result.task.artifacts[0].parts[0].data.days[0].day", equalTo("Monday"))
                // the SDK parses JSON numbers into floats
                .body("result.task.artifacts[0].parts[0].data.days[1].temperature", equalTo(27.0f))
                .body("result.task.status.state", equalTo("TASK_STATE_COMPLETED"));
    }
}
