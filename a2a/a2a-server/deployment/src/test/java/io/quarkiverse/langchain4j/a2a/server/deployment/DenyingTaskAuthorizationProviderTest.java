package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.WEATHER_QUESTION;
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
 * Without a provider of the application's own, every task operation is denied, the first message included, in test
 * mode as in any other. The client only sees {@code TaskNotFoundError}, so the application is told why at startup.
 */
public class DenyingTaskAuthorizationProviderTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, FakeChatModel.class, SunnyChatModel.class, TestAgent.class))
            .setLogRecordPredicate(record -> A2AServerRecorder.class.getName().equals(record.getLoggerName()));

    @RegisterAiService(chatLanguageModelSupplier = SunnyChatModel.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testTheFirstMessageIsDenied() {
        sendMessage(WEATHER_QUESTION).post()
                .body("result", nullValue())
                .body("error.data[0].reason", equalTo("TASK_NOT_FOUND"));
    }

    @Test
    public void testTheApplicationIsToldWhyAtStartup() {
        test.assertLogRecords(records -> assertThat(records)
                .filteredOn(record -> record.getLevel() == Level.WARNING)
                .extracting(LogRecord::getMessage)
                .singleElement()
                .asString()
                .contains("no TaskAuthorizationProvider", "a2a.authorization.required=false"));
    }
}
