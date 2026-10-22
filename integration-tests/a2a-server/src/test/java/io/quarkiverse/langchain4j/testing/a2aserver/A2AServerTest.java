package io.quarkiverse.langchain4j.testing.a2aserver;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

/**
 * Runs the extension inside a real application rather than a {@code QuarkusUnitTest}, so the Agent Card is served
 * by the transport's own routes over the actual HTTP server.
 */
@QuarkusTest
public class A2AServerTest {

    private static final String AGENT_CARD_PATH = "/.well-known/agent-card.json";

    @Test
    public void testAgentCardIsDiscoverableAtTheWellKnownUri() {
        given()
                .when().get(AGENT_CARD_PATH)
                .then()
                .statusCode(200)
                .body("name", equalTo("Weather Agent"))
                .body("description", equalTo("Answers questions about the weather"))
                .body("version", equalTo("1.2.3"));
    }

    @Test
    public void testAgentCardDeclaresTheSkill() {
        given()
                .when().get(AGENT_CARD_PATH)
                .then()
                .statusCode(200)
                .body("skills[0].id", equalTo("weather_search"))
                .body("skills[0].name", equalTo("Search weather"))
                .body("skills[0].tags", containsInAnyOrder("weather", "forecast"))
                .body("skills[0].examples", contains("weather in LA, CA"));
    }

    @Test
    public void testAgentCardDeclaresTheJsonRpcInterface() {
        given()
                .when().get(AGENT_CARD_PATH)
                .then()
                .statusCode(200)
                .body("supportedInterfaces[0].protocolBinding", equalTo("JSONRPC"))
                .body("supportedInterfaces[0].url", startsWith("http"))
                .body("supportedInterfaces[0].protocolVersion", equalTo("1.0"))
                .body("defaultInputModes", contains("text/plain"))
                .body("defaultOutputModes", contains("text/plain"));
    }

    /**
     * All three reference implementations are on the classpath, so the card carries one interface per transport,
     * JSON-RPC first as the preferred one. They share a URL because one HTTP server serves them all, and the port
     * has to be the one being listened on rather than the one configured.
     */
    @Test
    public void testAgentCardDeclaresEveryTransportOnTheClasspath() {
        given()
                .when().get(AGENT_CARD_PATH)
                .then()
                .statusCode(200)
                .body("supportedInterfaces.protocolBinding", contains("JSONRPC", "HTTP+JSON", "GRPC"))
                .body("supportedInterfaces.protocolVersion", everyItem(equalTo("1.0")))
                .body("supportedInterfaces.url", everyItem(endsWith(":" + RestAssured.port)));
    }

    /**
     * The JSON-RPC endpoint must answer on the root path with a protocol-level error rather than an HTTP one, which
     * is what tells us the transport is actually wired up.
     */
    @Test
    public void testJsonRpcEndpointIsServed() {
        given()
                .contentType("application/json")
                .header("A2A-Version", "1.0")
                .body("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"no/such/method\",\"params\":{}}")
                .when().post("/")
                .then()
                .statusCode(200)
                .body("error.code", equalTo(-32601));
    }

    /**
     * The whole round trip: an A2A client sends a message, the AI Service answers it, and the answer comes back as
     * a completed task. This is the path a successful response is serialized on, which none of the tests above
     * reach.
     */
    @Test
    public void testSendMessageIsAnsweredByTheAgent() {
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
                              "parts": [{ "text": "weather in LA?" }]
                            },
                            "configuration": { "returnImmediately": false }
                          }
                        }""")
                .when().post("/")
                .then()
                .statusCode(200)
                .body("error", nullValue())
                .body("result.task.artifacts[0].parts[0].text", equalTo("You asked: weather in LA?"))
                .body("result.task.status.state", equalTo("TASK_STATE_COMPLETED"));
    }

    /**
     * The specification has an agent assume version 0.3 when the client sends no {@code A2A-Version} header, and this
     * extension only serves 1.0. The SDK enforces that on every operation, so the client has to announce the
     * version it speaks before the agent will answer.
     *
     * @see <a href="https://a2a-protocol.org/latest/specification/#362-server-responsibilities">A2A spec §3.6.2 —
     *      Server Responsibilities</a>
     */
    @Test
    public void testSendMessageWithoutVersionHeaderIsRejected() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "jsonrpc": "2.0",
                          "id": "1",
                          "method": "SendMessage",
                          "params": {
                            "message": {
                              "messageId": "message-1",
                              "role": "ROLE_USER",
                              "parts": [{ "text": "weather in LA?" }]
                            }
                          }
                        }""")
                .when().post("/")
                .then()
                .statusCode(200)
                .body("error.data[0].reason", equalTo("VERSION_NOT_SUPPORTED"));
    }
}
