package io.quarkiverse.langchain4j.vertexai.gemini.deployment;

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

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.vertexai.runtime.gemini.VertexAiGeminiChatLanguageModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiGeminiChatLanguageModelSmokeTest {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "gemini-2.5-flash";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.gemini.log-requests", "true")
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(VertexAiGeminiChatLanguageModelSmokeTest::verifyLogRecords);

    @Inject
    ChatModel chatLanguageModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void test() {
        assertThat(ClientProxy.unwrap(chatLanguageModel)).isInstanceOf(VertexAiGeminiChatLanguageModel.class);

        mock.chat().reply(ChatReply.text("Nice to meet you").usage(11, 37));

        String response = chatLanguageModel.chat("hello");
        assertThat(response).isEqualTo("Nice to meet you");

        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo(String.format(
                "/vertexai-gemini/v1/projects/mock-project/locations/us-central1/publishers/google/models/%s:generateContent",
                CHAT_MODEL_ID));
        assertThat(request.header("Authorization")).isEqualTo("Bearer " + API_KEY);
        assertThat(request.header("User-Agent")).isEqualTo("Quarkus REST Client");
        assertThat(request.body()).contains("hello");
    }

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream()
                .map(record -> ((ExtLogRecord) record).getFormattedMessage())
                .filter(message -> message.startsWith("Request:")))
                .isNotEmpty()
                .allSatisfy(message -> assertThat(message).doesNotContain(API_KEY))
                .anySatisfy(message -> assertThat(message).contains("[Authorization: Beare...ey]"));
    }

    @Singleton
    public static class DummyAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer " + API_KEY;
        }

    }

}
