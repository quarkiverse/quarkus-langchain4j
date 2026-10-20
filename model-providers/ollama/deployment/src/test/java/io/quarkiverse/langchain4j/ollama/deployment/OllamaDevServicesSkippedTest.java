package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * With the mock enabled, an Ollama model without a base-url must not start the Ollama dev service (a container, or
 * pulling models into a local Ollama): its requests go to the mock.
 */
public class OllamaDevServicesSkippedTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "ollama")
            .overrideConfigKey("quarkus.langchain4j.embedding-model.provider", "ollama")
            .setLogRecordPredicate(r -> r.getLoggerName().contains("Ollama") && r.getLoggerName().contains("DevService"))
            .assertLogRecords(OllamaDevServicesSkippedTest::noOllamaDevServiceLogs);

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @Inject
    Config config;

    static void noOllamaDevServiceLogs(List<LogRecord> records) {
        assertThat(records).extracting(LogRecord::getMessage).isEmpty();
    }

    @Test
    void chatReachesTheMockWithoutTheOllamaDevService() {
        mock.reset();
        mock.chat().reply("from the mock");

        assertThat(chatModel.chat("hi")).isEqualTo("from the mock");

        assertThat(mock.chat().singleRequest().path()).isEqualTo("/ollama/api/chat");
        assertThat(config.getValue("quarkus.langchain4j.ollama.base-url", String.class))
                .isEqualTo(config.getValue("quarkus.langchain4j.mock-api.url", String.class) + "/ollama");
        assertThat(config.getOptionalValue("langchain4j-ollama-dev-service.ollama.host", String.class)).isEmpty();
        assertThat(config.getOptionalValue("langchain4j-ollama-dev-service.ollama.port", String.class)).isEmpty();
    }
}
