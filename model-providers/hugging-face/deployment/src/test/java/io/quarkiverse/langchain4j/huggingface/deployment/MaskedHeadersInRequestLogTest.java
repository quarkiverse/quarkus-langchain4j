package io.quarkiverse.langchain4j.huggingface.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class MaskedHeadersInRequestLogTest {

    private static final String API_KEY = "hf_api_abcdefghijklmnop";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.huggingface.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.huggingface.log-requests", "true")
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(MaskedHeadersInRequestLogTest::verifyLogRecords);

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void maskedHeadersAreLogged() {
        mock.chat().reply(ChatReply.text("Nice to meet you"));

        assertThat(chatModel.chat("hello")).isEqualTo("Nice to meet you");

        CapturedRequest loggedRequest = mock.chat().singleRequest();
        assertThat(loggedRequest.path()).isEqualTo("/huggingface/chat");
        assertThat(loggedRequest.query()).isNull();
        assertThat(loggedRequest.header("Authorization")).isEqualTo("Bearer " + API_KEY);
    }

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream()
                .map(record -> ((ExtLogRecord) record).getFormattedMessage())
                .filter(message -> message.startsWith("Request:")))
                .isNotEmpty()
                .allSatisfy(message -> assertThat(message).doesNotContain(API_KEY))
                .anySatisfy(message -> assertThat(message).contains("[Authorization: Beare...op]"));
    }
}
