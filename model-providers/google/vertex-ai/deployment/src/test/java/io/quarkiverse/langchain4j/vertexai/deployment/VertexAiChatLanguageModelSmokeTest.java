package io.quarkiverse.langchain4j.vertexai.deployment;

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
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.vertexai.runtime.VertexAiChatLanguageModel;
import io.quarkiverse.langchain4j.vertexai.runtime.VertxAiRestApi;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class VertexAiChatLanguageModelSmokeTest {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "chat-bison";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.vertexai.log-requests", "true")
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(VertexAiChatLanguageModelSmokeTest::verifyLogRecords);

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
        assertThat(ClientProxy.unwrap(chatLanguageModel)).isInstanceOf(VertexAiChatLanguageModel.class);

        mock.chat().reply(ChatReply.text("Nice to meet you").usage(11, 37));

        String response = chatLanguageModel.chat("hello");
        assertThat(response).isEqualTo("Nice to meet you");

        CapturedRequest loggedRequest = mock.chat().singleRequest();
        // the mock fills in the project-id and location the test leaves blank
        assertThat(loggedRequest.path()).isEqualTo(String.format(
                "/vertexai/v1/projects/mock-project/locations/us-central1/publishers/google/models/%s:predict",
                CHAT_MODEL_ID));
        assertThat(loggedRequest.query()).isNull();
        // the test's own AuthProvider wins over the mock's default one
        assertThat(loggedRequest.header("Authorization")).isEqualTo("Bearer " + API_KEY);
        assertThat(loggedRequest.header("User-Agent")).isEqualTo("Quarkus REST Client");
        assertThat(loggedRequest.body()).contains("hello");
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
    public static class DummyAuthProvider implements VertxAiRestApi.AuthProvider {
        @Override
        public String getBearerToken() {
            return API_KEY;
        }
    }

}
