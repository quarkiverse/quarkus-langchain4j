package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class SensitiveHeadersMaskedInRequestLogTest extends OpenAiBaseTest {

    private static final String API_KEY = "sk-secret-key-123456";
    private static final String GATEWAY_TOKEN = "tok-secret-1111";

    @ApplicationScoped
    public static class GatewayTokenCustomizer implements ModelBuilderCustomizer<OpenAiChatModel.OpenAiChatModelBuilder> {
        @Override
        public void customize(OpenAiChatModel.OpenAiChatModelBuilder builder) {
            builder.customHeaders(Map.of("X-Gateway-Token", GATEWAY_TOKEN));
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(GatewayTokenCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.log-requests", "true")
            .setLogRecordPredicate(record -> true)
            .assertLogRecords(SensitiveHeadersMaskedInRequestLogTest::verifyLogRecords);

    @Inject
    ChatModel chatModel;

    @BeforeEach
    void reset() {
        resetRequests();
    }

    @Test
    void apiKeyAndCustomHeadersAreMaskedInRequestLog() {
        setChatCompletionMessageContent("response");

        assertThat(chatModel.chat("hello")).isEqualTo("response");

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("Authorization")).isEqualTo("Bearer " + API_KEY);
        assertThat(loggedRequest.getHeader("X-Gateway-Token")).isEqualTo(GATEWAY_TOKEN);
    }

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        assertThat(logRecords.stream()
                .map(record -> ((ExtLogRecord) record).getFormattedMessage())
                .filter(message -> message.startsWith("Request:")))
                .isNotEmpty()
                .allSatisfy(message -> assertThat(message)
                        .doesNotContain(API_KEY)
                        .doesNotContain(GATEWAY_TOKEN))
                .anySatisfy(message -> assertThat(message)
                        .contains("[Authorization: Beare...56]")
                        .contains("[X-Gateway-Token: tok-s...11]"));
    }
}
