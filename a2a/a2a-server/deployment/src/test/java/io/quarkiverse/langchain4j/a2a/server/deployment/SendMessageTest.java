package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Drives a real {@code SendMessage} call through the JSON-RPC transport, so the whole chain is exercised: the
 * generated executor reads the client's text, invokes the AI Service, and emits the answer as an artifact.
 */
public class SendMessageTest extends OpenAiBaseTest {

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

        @SystemMessage("You are a weather assistant.")
        String chat(@UserMessage String question);
    }

    private static final String SEND_MESSAGE = """
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
            }""";

    /**
     * Both tests below drive a model call, and the one asserting on the request the model received expects a single
     * one in the log, so each of them starts from an empty log whichever order they run in.
     */
    @BeforeEach
    public void forgetEarlierRequests() {
        resetRequests();
    }

    @Test
    public void testTheAgentAnswersOverJsonRpc() {
        setChatCompletionMessageContent("It is sunny in LA.");

        given()
                .contentType("application/json")
                .header("A2A-Version", "1.0")
                .body(SEND_MESSAGE)
                .when().post("/")
                .then()
                .statusCode(200)
                .body("error", nullValue())
                .body("result", notNullValue())
                .body("result.task.artifacts[0].parts[0].text", equalTo("It is sunny in LA."))
                .body("result.task.status.state", equalTo("TASK_STATE_COMPLETED"));
    }

    /**
     * The text the client sent must reach the AI Service as the user message, which is what makes the parameter
     * mapping meaningful rather than incidental.
     */
    @Test
    public void testTheClientTextReachesTheModel() throws Exception {
        setChatCompletionMessageContent("It is sunny in LA.");

        given()
                .contentType("application/json")
                .header("A2A-Version", "1.0")
                .body(SEND_MESSAGE)
                .when().post("/")
                .then()
                .statusCode(200);

        assertMessages(getRequestAsMap(), messages -> assertThat(messages)
                .last()
                .satisfies(message -> {
                    assertThat(message).containsEntry("role", "user");
                    assertThat(message).containsEntry("content", "what is the weather in LA?");
                }));
    }
}
