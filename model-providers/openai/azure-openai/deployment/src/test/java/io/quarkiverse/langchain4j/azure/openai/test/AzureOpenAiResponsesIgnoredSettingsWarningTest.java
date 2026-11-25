package io.quarkiverse.langchain4j.azure.openai.test;

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
import io.quarkiverse.langchain4j.azure.openai.runtime.AzureOpenAiRecorder;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Settings that only the Chat Completions path supports must not be dropped silently in Responses mode: a warning
 * listing them is logged at startup.
 */
public class AzureOpenAiResponsesIgnoredSettingsWarningTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "azure-openai")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.endpoint-style", "v1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.mode", "responses")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.presence-penalty", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.seed", "7")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.max-retries", "3")
            .setLogRecordPredicate(record -> AzureOpenAiRecorder.class.getName().equals(record.getLoggerName()))
            .assertLogRecords(AzureOpenAiResponsesIgnoredSettingsWarningTest::verifyLogRecords);

    private static void verifyLogRecords(List<LogRecord> records) {
        assertThat(records)
                // JBoss LogManager levels are distinct objects from the JUL ones, so compare the numeric level
                .filteredOn(record -> record.getLevel().intValue() == Level.WARNING.intValue())
                .map(AzureOpenAiResponsesIgnoredSettingsWarningTest::formatted)
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .contains("not supported by the Responses API")
                        .contains("chat-model.presence-penalty, chat-model.seed, max-retries")
                        .doesNotContain("frequency-penalty")
                        .contains("quarkus.langchain4j.azure-openai.chat-model.mode=chat-completion"));
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
