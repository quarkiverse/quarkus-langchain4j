package io.quarkiverse.langchain4j.huggingface.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.huggingface.HuggingFaceModelName;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class MaskedHeadersInRequestLogTest extends WiremockAware {

    private static final String API_KEY = "hf_api_abcdefghijklmnop";
    private static final String CHAT_MODEL_ID = HuggingFaceModelName.TII_UAE_FALCON_7B_INSTRUCT;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.huggingface.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.huggingface.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.huggingface.chat-model.inference-endpoint-url",
                    WiremockAware.wiremockUrlForConfig("/models/" + CHAT_MODEL_ID))
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(MaskedHeadersInRequestLogTest::verifyLogRecords);

    @Inject
    ChatModel chatModel;

    @Test
    void maskedHeadersAreLogged() {
        wiremock().register(
                post(urlEqualTo("/models/" + CHAT_MODEL_ID))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        [
                                          {
                                            "generated_text": "Nice to meet you"
                                          }
                                        ]
                                        """)));

        assertThat(chatModel.chat("hello")).isEqualTo("Nice to meet you");

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("Authorization")).isEqualTo("Bearer " + API_KEY);
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
