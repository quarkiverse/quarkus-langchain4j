package io.quarkiverse.langchain4j.anthropic.deployment;

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

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

class AnthropicChatLanguageModelSmokeTest extends AnthropicSmokeTest {

    private static final String ANSWER = "Hello! As an AI language model, I don't have feelings or a physical state, but I'm functioning properly and ready to assist you with any questions or tasks you may have. How can I help you today?";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.anthropic.log-responses", "true")
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(AnthropicChatLanguageModelSmokeTest::verifyLogRecords);

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void blocking() {
        assertThat(ClientProxy.unwrap(chatModel))
                .isInstanceOf(AnthropicChatModel.class);

        mock.chat().reply(ChatReply.text(ANSWER).usage(14, 47));

        var response = chatModel.chat("Hello, how are you today?");
        assertThat(response)
                .isNotNull()
                .isEqualTo(ANSWER);

        assertThat(mock.requests())
                .hasSize(1);

        CapturedRequest loggedRequest = mock.chat().singleRequest();
        assertThat(loggedRequest.path()).isEqualTo("/anthropic/v1/messages");
        assertThat(loggedRequest.header("User-Agent"))
                .isEqualTo("Quarkus REST Client");
        assertThat(loggedRequest.header("x-api-key")).isEqualTo(API_KEY);
        assertThat(loggedRequest.header("anthropic-version")).isNotNull();

        var requestBody = """
                {
                  "model" : "claude-3-haiku-20240307",
                  "messages" : [ {
                    "role" : "user",
                    "content" : [ {
                      "type" : "text",
                      "text" : "Hello, how are you today?"
                    } ]
                  } ],
                  "max_tokens" : 1024,
                  "stream" : false
                }""";

        assertThat(loggedRequest.body())
                .isEqualTo(requestBody)
                .contains(CHAT_MODEL_ID);
    }

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream()
                .map(record -> ((ExtLogRecord) record).getFormattedMessage())
                .filter(message -> message.startsWith("Request:")))
                .isNotEmpty()
                .allSatisfy(message -> assertThat(message).doesNotContain(API_KEY))
                .anySatisfy(message -> assertThat(message).contains("[x-api-key: somek...ey]"));
    }
}
