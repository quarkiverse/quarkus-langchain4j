package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
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
 * A client that asks for {@code returnImmediately} gets the task back while the agent is still working on it, and
 * can cancel it then. Cancelling does not stop the call into the model, which returns later with an answer for a
 * task the client was already told is cancelled.
 */
public class CancelRunningTaskTest {

    static final CountDownLatch MODEL_CALLED = new CountDownLatch(1);
    static final CountDownLatch RELEASE_MODEL = new CountDownLatch(1);

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class, BlockingChatModelSupplier.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent");

    /**
     * Holds the answer back until the test has cancelled the task, so that the answer arrives after the cancellation
     * rather than racing it.
     */
    public static class BlockingChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    MODEL_CALLED.countDown();
                    try {
                        RELEASE_MODEL.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return ChatResponse.builder().aiMessage(new AiMessage("It is sunny in LA.")).build();
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = BlockingChatModelSupplier.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @AfterEach
    void releaseTheModel() {
        RELEASE_MODEL.countDown();
    }

    @Test
    public void testACancelledTaskStaysCancelledWhenTheAgentAnswersLater() throws InterruptedException {
        String taskId = given()
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
                            "configuration": { "returnImmediately": true }
                          }
                        }""")
                .when().post("/")
                .then()
                .statusCode(200)
                .extract().path("result.task.id");
        assertThat(MODEL_CALLED.await(10, TimeUnit.SECONDS)).isTrue();

        given()
                .contentType("application/json")
                .header("A2A-Version", "1.0")
                .body("{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"CancelTask\",\"params\":{\"id\":\"" + taskId
                        + "\"}}")
                .when().post("/")
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("TASK_STATE_CANCELED"));

        RELEASE_MODEL.countDown();
        // the answer has nothing to signal its arrival by, so the task is read back once it has had time to land
        Thread.sleep(1000);

        given()
                .contentType("application/json")
                .header("A2A-Version", "1.0")
                .body("{\"jsonrpc\":\"2.0\",\"id\":\"3\",\"method\":\"GetTask\",\"params\":{\"id\":\"" + taskId
                        + "\"}}")
                .when().post("/")
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("TASK_STATE_CANCELED"))
                .body("result.artifacts", anyOf(nullValue(), empty()));
    }
}
