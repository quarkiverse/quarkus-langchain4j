package io.quarkiverse.langchain4j.ollama.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;

public class MaskedHeadersInRequestLogTest extends WiremockAware {

    private static final String AUTHORIZATION_HEADER_VALUE = "Bearer test";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(Assistant.class))
            .overrideConfigKey("quarkus.langchain4j.ollama.base-url", WiremockAware.wiremockUrlForConfig())
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

    @Test
    void maskedHeadersAreLogged() {
        wiremock().register(
                post(urlEqualTo("/api/chat"))
                        .withHeader("Authorization", equalTo(AUTHORIZATION_HEADER_VALUE))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/x-ndjson")
                                .withBody(
                                        """
                                                {"model":"llama3.2","created_at":"2024-11-30T09:03:42.312611426Z","message":{"role":"assistant","content":"Nice to "},"done":false}
                                                {"model":"llama3.2","created_at":"2024-11-30T09:03:44.109059873Z","message":{"role":"assistant","content":"meet you"},"done_reason":"stop","done":true,"total_duration":4821417857,"load_duration":2508844071,"prompt_eval_count":11,"prompt_eval_duration":514000000,"eval_count":10,"eval_duration":1797000000}""")));

        List<String> response = assistant.streaming("hello").collect().asList().await().indefinitely();
        assertThat(response).containsExactly("Nice to ", "meet you");

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("Authorization")).isEqualTo(AUTHORIZATION_HEADER_VALUE);
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
