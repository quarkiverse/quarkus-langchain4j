package io.quarkiverse.langchain4j.agentic.deployment.validation;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class DuplicateSupervisorSubAgentNamesTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .setLogRecordPredicate(DuplicateSupervisorSubAgentNamesTest::isAgenticWarning)
            .assertLogRecords(DuplicateSupervisorSubAgentNamesTest::assertWarnsAboutSharedAgentName);

    private static boolean isAgenticWarning(LogRecord record) {
        return record.getLevel().intValue() >= Level.WARNING.intValue()
                && record.getLoggerName() != null
                && record.getLoggerName().contains("langchain4j.agentic");
    }

    private static void assertWarnsAboutSharedAgentName(List<LogRecord> records) {
        Assertions.assertThat(records).anySatisfy(record -> Assertions.assertThat(record.getMessage())
                .contains("share the agent name")
                .contains("editStory"));
    }

    @Test
    void test() {
    }

    public interface AudienceEditor {

        @UserMessage("""
                Rewrite the following story to better align with the target audience of {{audience}}.
                The story is "{{story}}".
                """)
        @Agent(description = "Edit a story to better fit a given audience", outputKey = "story")
        String editStory(@V("story") String story, @V("audience") String audience);
    }

    public interface StyleEditor {

        @UserMessage("""
                Rewrite the following story to better fit the {{style}} style.
                The story is "{{story}}".
                """)
        @Agent(description = "Edit a story to better fit a given style", outputKey = "story")
        String editStory(@V("story") String story, @V("style") String style);
    }

    public interface SupervisorStoryEditor {

        @SupervisorAgent(subAgents = { AudienceEditor.class, StyleEditor.class })
        String edit(@V("request") String request);
    }
}
