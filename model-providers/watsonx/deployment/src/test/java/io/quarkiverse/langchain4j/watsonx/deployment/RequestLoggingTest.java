package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.COS_PATH;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_API;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.textprocessing.schema.create.CreateSchemaService;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkus.test.QuarkusExtensionTest;

public class RequestLoggingTest extends WatsonxMockTestBase {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.chat-model.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.log-requests-curl", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.cos-url",
                    "${quarkus.langchain4j.mock-api.url}" + COS_PATH)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.document-reference.connection", "connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.document-reference.bucket-name", "bucket")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.results-reference.connection", "connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-extraction.results-reference.bucket-name", "bucket")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.cos-url",
                    "${quarkus.langchain4j.mock-api.url}" + COS_PATH)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.document-reference.connection",
                    "connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.text-classification.document-reference.bucket-name",
                    "bucket")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.cos-url",
                    "${quarkus.langchain4j.mock-api.url}" + COS_PATH)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.document-reference.connection", "connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.schema.create.document-reference.bucket-name", "bucket")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.quiet.text-extraction.cos-url",
                    "${quarkus.langchain4j.mock-api.url}" + COS_PATH)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.quiet.text-extraction.document-reference.connection",
                    "connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.quiet.text-extraction.document-reference.bucket-name",
                    "bucket")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.quiet.text-extraction.results-reference.connection",
                    "connection")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.quiet.text-extraction.results-reference.bucket-name",
                    "bucket")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.quiet.text-extraction.log-requests-curl", "false")
            .setLogRecordPredicate(new Predicate<LogRecord>() {
                @Override
                public boolean test(LogRecord record) {
                    return record.getLoggerName().endsWith("WatsonxClientLogger");
                }
            })
            .assertLogRecords(new Consumer<List<LogRecord>>() {
                @Override
                public void accept(List<LogRecord> records) {
                    verifyLogRecords(records);
                }
            })
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class).addClass(WatsonxTestUtil.class);
                }
            });

    @Inject
    ChatModel chatModel;

    @Inject
    TextExtractionService extraction;

    @Inject
    TextClassificationService classification;

    @Inject
    CreateSchemaService schema;

    @Inject
    @ModelName("quiet")
    TextExtractionService quiet;

    @Test
    void maskedHeadersAreLogged() {
        mock.chat().reply(ChatReply.text("AI Response").usage(59, 47));

        assertThat(chatModel.chat("Hello")).isEqualTo("AI Response");
        assertWatsonxRequest(mock.chat().singleRequest(), URL_WATSONX_CHAT_API);
    }

    @Test
    void logsLazyStorageRequestsWithTheServiceConfiguration() throws Exception {
        for (String file : List.of("extraction.pdf", "classification.pdf", "schema.pdf", "quiet.pdf")) {
            mock.raw().on("PUT", COS_PATH + "/bucket/" + file).reply(200, "");
            mock.raw().on("DELETE", COS_PATH + "/bucket/" + file).reply(204, "");
        }
        assertThat(extraction.uploadFile(new ByteArrayInputStream(new byte[] { 1 }), "extraction.pdf")).isTrue();
        assertThat(classification.uploadFile(new ByteArrayInputStream(new byte[] { 1 }), "classification.pdf")).isTrue();
        assertThat(schema.uploadFile(new ByteArrayInputStream(new byte[] { 1 }), "schema.pdf")).isTrue();
        assertThat(quiet.uploadFile(new ByteArrayInputStream(new byte[] { 1 }), "quiet.pdf")).isTrue();
        assertThat(extraction.deleteFile("bucket", "extraction.pdf")).isTrue();
        assertThat(classification.deleteFile("bucket", "classification.pdf")).isTrue();
        assertThat(schema.deleteFile("bucket", "schema.pdf")).isTrue();
        assertThat(quiet.deleteFile("bucket", "quiet.pdf")).isTrue();
        for (String file : List.of("extraction.pdf", "classification.pdf", "schema.pdf", "quiet.pdf")) {
            for (String method : List.of("PUT", "DELETE")) {
                List<CapturedRequest> requests = requests(method, COS_PATH + "/bucket/" + file);
                assertThat(requests).hasSize(1);
                assertThat(requests.get(0).header("Authorization")).isEqualTo("Bearer " + MOCK_TOKEN);
            }
        }
    }

    private static void verifyLogRecords(List<LogRecord> records) {
        List<String> messages = new ArrayList<>();
        List<String> requestMessages = new ArrayList<>();
        List<String> cosMessages = new ArrayList<>();
        for (LogRecord record : records) {
            String message = ((ExtLogRecord) record).getFormattedMessage();
            messages.add(message);
            if (message.startsWith("Request:")) {
                requestMessages.add(message);
                assertThat(message).doesNotContain(MOCK_TOKEN);
            }
            if (message.contains(COS_PATH + "/")) {
                cosMessages.add(message);
            }
        }
        assertThat(requestMessages).isNotEmpty();
        assertThat(requestMessages.toString()).contains("[Authorization: Beare...en]");
        assertThat(messages.toString()).doesNotContain("quiet.pdf");
        assertThat(cosMessages.toString())
                .containsPattern("curl -X PUT 'http://[^']+/cos/bucket/extraction\\.pdf'")
                .containsPattern("curl -X PUT 'http://[^']+/cos/bucket/classification\\.pdf'")
                .containsPattern("curl -X PUT 'http://[^']+/cos/bucket/schema\\.pdf'")
                .containsPattern("curl -X DELETE 'http://[^']+/cos/bucket/extraction\\.pdf'")
                .containsPattern("curl -X DELETE 'http://[^']+/cos/bucket/classification\\.pdf'")
                .containsPattern("curl -X DELETE 'http://[^']+/cos/bucket/schema\\.pdf'")
                .doesNotContain("Request:", "Response:");
    }
}
