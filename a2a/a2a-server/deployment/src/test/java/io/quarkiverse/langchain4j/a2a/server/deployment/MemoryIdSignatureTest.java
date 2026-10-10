package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import jakarta.enterprise.context.ApplicationScoped;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An AI Service taking a memory id alongside the message is served end to end: the generated executor fills the
 * memory id from the request's {@code contextId} and the message from its text.
 * <p>
 * That mapping is what makes an A2A conversation a conversation. The client never names a memory, it names a
 * context, and the two have to be the same thing for the agent to remember what was said and to keep separate
 * clients apart.
 * <p>
 * The AI Service here is {@link ApplicationScoped} on purpose. One left in the default request scope has its chat
 * memory cleared when the request that created it ends, which is fine for one-shot calls but leaves an agent
 * unable to hold a conversation across A2A messages — each of which arrives on a request of its own.
 */
public class MemoryIdSignatureTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent");

    @ApplicationScoped
    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "chat", name = "Chat", description = "Chats", tags = "chat"))
    public interface TestAgent {

        String chat(@MemoryId String conversationId, @UserMessage String question);
    }

    /**
     * These tests send more than one message and always assert on the request the model received last, so each of
     * them starts from an empty request log.
     */
    @BeforeEach
    public void forgetEarlierRequests() {
        resetRequests();
    }

    @Test
    public void testTheAgentAnswersWhenAMemoryIdIsPresent() throws Exception {
        setChatCompletionMessageContent("Noted.");

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
                              "contextId": "context-from-a2a",
                              "role": "ROLE_USER",
                              "parts": [{ "text": "my name is John Doe" }]
                            },
                            "configuration": { "returnImmediately": false }
                          }
                        }""")
                .when().post("/")
                .then()
                .statusCode(200)
                .body("error", nullValue())
                .body("result.task.artifacts[0].parts[0].text", equalTo("Noted."))
                .body("result.task.status.state", equalTo("TASK_STATE_COMPLETED"));

        // the message parameter must still receive the client's text, not the memory id
        assertSingleRequestMessage(getRequestAsMap(), "my name is John Doe");
    }

    /**
     * Two messages sharing a {@code contextId} are one conversation, so the second call has to carry what was said
     * in the first. Without this the agent would meet every message as if it were the first.
     */
    @Test
    public void testMessagesSharingAContextAreOneConversation() throws Exception {
        setChatCompletionMessageContent("Noted.");
        sendMessage("shared-context", "message-1", "my name is John Doe");

        resetRequests();
        setChatCompletionMessageContent("You are John Doe.");
        sendMessage("shared-context", "message-2", "what is my name?");

        assertMessages(getRequestAsMap(), messages -> assertThat(messages)
                .extracting(message -> message.get("content"))
                .containsExactly("my name is John Doe", "Noted.", "what is my name?"));
    }

    /**
     * The other half of the same contract: two contexts are two conversations. A client must not reach what another
     * one said by sending a message of its own.
     */
    @Test
    public void testASecondContextStartsWithNoHistory() throws Exception {
        setChatCompletionMessageContent("Noted.");
        sendMessage("first-context", "message-1", "my name is Jane Roe");

        resetRequests();
        setChatCompletionMessageContent("I do not know.");
        sendMessage("second-context", "message-2", "what is my name?");

        assertSingleRequestMessage(getRequestAsMap(), "what is my name?");
    }

    private static void sendMessage(String contextId, String messageId, String text) {
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
                              "messageId": "%s",
                              "contextId": "%s",
                              "role": "ROLE_USER",
                              "parts": [{ "text": "%s" }]
                            },
                            "configuration": { "returnImmediately": false }
                          }
                        }""".formatted(messageId, contextId, text))
                .when().post("/")
                .then()
                .statusCode(200)
                .body("error", nullValue());
    }
}
