package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.openai.runtime.OpenAiRecorder;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * When the Responses API is selected automatically, settings that only the chat-completions path supports must not be
 * dropped silently: a warning listing them is logged at startup.
 */
public class ResponsesAutoModeIgnoredSettingsWarningTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            // default base URL (api.openai.com) and no mode -> responses, auto-selected
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "my-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.presence-penalty", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.stop", "END")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.log-requests-curl", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.max-retries", "3")
            .setLogRecordPredicate(record -> OpenAiRecorder.class.getName().equals(record.getLoggerName()))
            .assertLogRecords(ResponsesAutoModeIgnoredSettingsWarningTest::verifyLogRecords);

    private static void verifyLogRecords(List<LogRecord> records) {
        assertThat(records)
                // JBoss LogManager levels are distinct objects from the JUL ones, so compare the numeric level
                .filteredOn(record -> record.getLevel().intValue() == Level.WARNING.intValue())
                .map(ResponsesAutoModeIgnoredSettingsWarningTest::formatted)
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .contains("not supported by the Responses API")
                        .contains("chat-model.presence-penalty, chat-model.stop, log-requests-curl, max-retries")
                        .doesNotContain("frequency-penalty")
                        .contains("quarkus.langchain4j.openai.chat-model.mode=chat-completion"));
    }

    private static String formatted(LogRecord record) {
        return record.getParameters() == null ? record.getMessage()
                : String.format(record.getMessage(), record.getParameters());
    }

    @Inject
    ChatModel chatModel;

    @Test
    void warningIsLoggedAtStartup() {
        // the assertion happens in verifyLogRecords once the application has started
        assertThat(chatModel).isNotNull();
    }
}
