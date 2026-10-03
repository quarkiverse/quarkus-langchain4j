package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.WEATHER_QUESTION;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.cancelTask;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.getTask;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.post;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogRecord;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.a2a.server.runtime.executor.QuarkusBaseAgentExecutor;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A client that asks for {@code returnImmediately} gets the task back while the agent is still working on it, and
 * can cancel it then. Cancelling does not stop the call into the model, which returns later with an answer for a
 * task the client was already told is cancelled. The task is read back too, but the SDK stores a late artifact
 * asynchronously, so the executor's log of dropping the answer is what proves it was dropped.
 */
public class CancelRunningTaskTest {

    static final CountDownLatch MODEL_CALLED = new CountDownLatch(1);
    static final CountDownLatch RELEASE_MODEL = new CountDownLatch(1);
    static final CountDownLatch EXECUTION_FINISHED = new CountDownLatch(1);

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, TestAgent.class, FakeChatModel.class, BlockingChatModel.class, ExecutionTracker.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent")
            .overrideRuntimeConfigKey("a2a.authorization.required", "false")
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse.langchain4j.a2a.server.runtime.executor\".level", "DEBUG")
            .setLogRecordPredicate(record -> QuarkusBaseAgentExecutor.class.getName().equals(record.getLoggerName()));

    /**
     * Holds the answer back until the test has cancelled the task, so that the answer arrives after the cancellation
     * rather than racing it.
     */
    public static class BlockingChatModel extends FakeChatModel {

        @Override
        protected String answer(ChatRequest request) {
            MODEL_CALLED.countDown();
            try {
                RELEASE_MODEL.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "It is sunny in LA.";
        }
    }

    /**
     * Tells the test when the executor is done with the late answer, which is the moment the task can be read back
     * without guessing how long that takes. {@code cancel} is delegated explicitly because {@link AgentExecutor}
     * gives it a default body, which a decorator would otherwise inherit instead of reaching the executor's own.
     */
    @Decorator
    @Priority(1)
    public static abstract class ExecutionTracker implements AgentExecutor {

        @Inject
        @Delegate
        @Any
        AgentExecutor delegate;

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
            try {
                delegate.execute(context, emitter);
            } finally {
                EXECUTION_FINISHED.countDown();
            }
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
            delegate.cancel(context, emitter);
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = BlockingChatModel.class)
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
        String taskId = sendMessage(WEATHER_QUESTION).returnImmediately().post().extract().path("result.task.id");
        assertThat(MODEL_CALLED.await(10, TimeUnit.SECONDS)).isTrue();

        post(cancelTask(taskId))
                .body("result.status.state", equalTo("TASK_STATE_CANCELED"));

        RELEASE_MODEL.countDown();
        assertThat(EXECUTION_FINISHED.await(10, TimeUnit.SECONDS)).isTrue();

        post(getTask(taskId))
                .body("result.status.state", equalTo("TASK_STATE_CANCELED"))
                .body("result.artifacts", anyOf(nullValue(), empty()));

        test.assertLogRecords(records -> assertThat(records)
                .extracting(LogRecord::getMessage)
                .anyMatch(message -> message.contains("Dropping the answer for task " + taskId)));
    }
}
