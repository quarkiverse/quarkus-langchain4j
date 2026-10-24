package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;

public class MaskedHeadersInRequestLogTest {

    private static final String AUTHORIZATION_HEADER_VALUE = "Bearer test";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Assistant.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.devservices.enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-requests", "true")
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(MaskedHeadersInRequestLogTest::verifyLogRecords);

    @Singleton
    @RegisterAiService
    interface Assistant {
        Multi<String> streaming(@dev.langchain4j.service.UserMessage String text);
    }

    @Inject
    Assistant assistant;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void maskedHeadersAreLogged() {
        mock.chat().reply(ChatReply.text("Nice to meet you").chunks(List.of("Nice to ", "meet you")));

        List<String> response = assistant.streaming("hello").collect().asList().await().indefinitely();
        assertThat(response).containsExactly("Nice to ", "meet you");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/ollama/api/chat");
        assertThat(request.header("Authorization")).isEqualTo(AUTHORIZATION_HEADER_VALUE);
    }

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream()
                .map(record -> ((ExtLogRecord) record).getFormattedMessage())
                .filter(message -> message.startsWith("Request:")))
                .isNotEmpty()
                .allSatisfy(message -> assertThat(message).doesNotContain(AUTHORIZATION_HEADER_VALUE))
                .anySatisfy(message -> assertThat(message).contains("[Authorization: Beare...st]"));
    }

    @Singleton
    public static class OllamaClientAuthHeaderFilter implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return AUTHORIZATION_HEADER_VALUE;
        }
    }
}
