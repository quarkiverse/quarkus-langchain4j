package io.quarkiverse.langchain4j.tavily.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

public class TavilyLoggingTest extends WiremockAware {

    private static final String API_KEY = "tvly-SECRETKEY123456";

    private static final String MASKED_API_KEY = "tvly-SE************";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(new Supplier<>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class);
                }
            })
            .overrideConfigKey("quarkus.langchain4j.tavily.api-key", API_KEY)
            .overrideConfigKey("quarkus.langchain4j.tavily.base-url", WiremockAware.wiremockUrlForConfig())
            .overrideConfigKey("quarkus.langchain4j.tavily.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.tavily.log-responses", "true")
            .setLogRecordPredicate(TavilyLoggingTest::isTavilyRecord)
            .assertLogRecords(TavilyLoggingTest::verifyLogRecords);

    @Inject
    WebSearchEngine webSearchEngine;

    @BeforeEach
    public void setup() {
        wiremock().register(
                post(urlEqualTo("/search"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                            "query": "logging",
                                            "answer": null,
                                            "results": [
                                                {
                                                    "title": "Logged",
                                                    "url": "https://example.com/logged",
                                                    "content": "logged",
                                                    "score": 0.9
                                                }
                                            ],
                                            "response_time": 0.1
                                        }
                                        """)));
    }

    @Test
    public void testRequestAndResponseAreLoggedWithoutApiKey() {
        WebSearchResults result = webSearchEngine.search(WebSearchRequest.from("logging"));

        assertThat(result.results()).hasSize(1);
    }

    private static boolean isTavilyRecord(LogRecord record) {
        return record.getLoggerName().startsWith("io.quarkiverse.langchain4j.tavily");
    }

    private static void verifyLogRecords(List<LogRecord> logRecords) {
        boolean requestMasked = false;
        boolean responseLogged = false;
        for (LogRecord record : logRecords) {
            String message = ((ExtLogRecord) record).getFormattedMessage();
            assertThat(message).doesNotContain(API_KEY);
            requestMasked |= message.startsWith("Request:") && message.contains(MASKED_API_KEY);
            responseLogged |= message.startsWith("Response:") && message.contains("\"logged\"");
        }
        assertThat(requestMasked).as("masked request log").isTrue();
        assertThat(responseLogged).as("response log").isTrue();
    }
}
