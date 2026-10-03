package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.WEATHER_QUESTION;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendStreamingMessage;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An agent advertising {@code streaming} has to actually serve {@code SendStreamingMessage}, where the client is
 * told how the task progresses instead of waiting for one response. The exposed method returns once, so what is
 * incremental here is the task's lifecycle rather than the answer, and that is what the card promises.
 */
public class StreamingTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, TestAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent")
            .overrideRuntimeConfigKey("a2a.authorization.required", "false");

    @RegisterAiService
    @ExposeA2AAgent(streaming = true, skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testTheClientIsToldHowTheTaskProgresses() {
        setChatCompletionMessageContent("It is sunny in LA.");

        String events = sendStreamingMessage(WEATHER_QUESTION).post()
                .contentType("text/event-stream")
                .extract().asString();

        assertThat(events)
                .contains("TASK_STATE_SUBMITTED")
                .contains("TASK_STATE_WORKING")
                .contains("TASK_STATE_COMPLETED");
    }

    /**
     * The answer reaches the client on the stream too, as the artifact the task produced. A stream carrying only
     * state changes would tell a client the work finished without ever handing over its result.
     */
    @Test
    public void testTheAnswerArrivesOnTheStreamAsAnArtifact() {
        setChatCompletionMessageContent("It is sunny in LA.");

        String events = sendStreamingMessage(WEATHER_QUESTION).post()
                .extract().asString();

        assertThat(events).contains("artifactUpdate").contains("It is sunny in LA.");
    }

    /**
     * The states have to arrive in the order they happen: a client that sees completion before the artifact has no
     * way to know more is coming.
     */
    @Test
    public void testTheStatesArriveInOrder() {
        setChatCompletionMessageContent("It is sunny in LA.");

        String events = sendStreamingMessage(WEATHER_QUESTION).post()
                .extract().asString();

        assertThat(events.indexOf("TASK_STATE_SUBMITTED")).isLessThan(events.indexOf("TASK_STATE_WORKING"));
        assertThat(events.indexOf("TASK_STATE_WORKING")).isLessThan(events.indexOf("artifactUpdate"));
        assertThat(events.indexOf("artifactUpdate")).isLessThan(events.indexOf("TASK_STATE_COMPLETED"));
    }
}
