package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.WEATHER_QUESTION;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.getTask;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.post;
import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.a2a.server.runtime.A2AServerRecorder;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Turning task authorization off sets the provider that denies everything aside, so any caller can send a message
 * and read back the task. The application is reminded of it at startup.
 */
public class AuthorizationOffTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, FakeChatModel.class, SunnyChatModel.class, TestAgent.class))
            .overrideRuntimeConfigKey("a2a.authorization.required", "false")
            .setLogRecordPredicate(record -> A2AServerRecorder.class.getName().equals(record.getLoggerName()));

    @RegisterAiService(chatLanguageModelSupplier = SunnyChatModel.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testAnyCallerReadsTheTaskBack() {
        String taskId = sendMessage(WEATHER_QUESTION).post()
                .body("error", nullValue())
                .extract().path("result.task.id");

        post(getTask(taskId))
                .body("error", nullValue())
                .body("result.artifacts[0].parts[0].text", equalTo("It is sunny."));
    }

    @Test
    public void testTheApplicationIsRemindedAtStartup() {
        test.assertLogRecords(records -> assertThat(records)
                .filteredOn(record -> record.getLevel() == Level.WARNING)
                .extracting(LogRecord::getMessage)
                .singleElement()
                .asString()
                .contains("A2A task authorization is off", "can list and read every task"));
    }
}
