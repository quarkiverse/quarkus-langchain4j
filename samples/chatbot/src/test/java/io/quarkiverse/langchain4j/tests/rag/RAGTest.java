package io.quarkiverse.langchain4j.tests.rag;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class RAGTest {
    private static final Logger LOG = Logger.getLogger(RAGTest.class);

    private List<String> answers;
    private WebSocket webSocket;
    private HttpClient client;

    @TestHTTPResource
    URL url;

    @BeforeEach
    void setUp() throws URISyntaxException, ExecutionException, InterruptedException {
        String socket = "/chatbot";
        URI uri = new URI("ws", null,
                url.getHost(),
                url.getPort(),
                socket, null, null);
        LOG.info("Connecting to: " + socket + " at " + uri);
        answers = Collections.synchronizedList(new ArrayList<>());
        client = HttpClient.newHttpClient();
        webSocket = client.newWebSocketBuilder()
                .buildAsync(uri, new WebSocketListener(answers)).get();
    }

    @AfterEach
    void tearDown() throws Exception {
        answers = null;
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").get();
    }

    @Test
    public void smoke() {
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Assertions.assertFalse(answers.isEmpty());
        });
        Assertions.assertEquals("Hello, I'm Bob, how can I help you?", answers.get(0));
    }

    @Test
    public void documentBasedAnswer() throws InterruptedException, URISyntaxException, IOException {
        // Input tokens are usually much cheaper, than output tokens, so let's stop the LLM from talking too much
        String prompt = "What is the opening deposit (in USD) for a standard savings account? Answer with number only";
        webSocket.sendText(prompt, true);

        // The answer is split to many messages due to Multi<String> in the Bot.
        // We need to wait for the end of the answer, detected as no new messages during a second.
        // The prompt should protect against this, but it is not guaranteed.
        int repeats = 0;
        int lastSize = 0;
        while (answers.size() <= 1 || answers.size() != lastSize) {
            if (repeats++ > 10) {
                LOG.warn("We have waited for: " + repeats + " seconds and it is too much!");
                break;
            }
            lastSize = answers.size();
            Thread.sleep(1000);
        }

        String response = String.join("", answers);
        assertTrue(response.contains("25"));

        // Now, lets verify, that ai metrics were created:
        URI uri = new URI("http", null,
                url.getHost(),
                url.getPort(),
                "/q/metrics", null, null);
        HttpResponse<String> metricsResponse = client.send(HttpRequest.newBuilder().GET().uri(uri).build(),
                HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, metricsResponse.statusCode());
        LinesArray metrics = new LinesArray(Stream.of(metricsResponse.body().split("\n"))
                .filter(line -> line.startsWith("gen_ai"))
                .toList());
        assertTrue(metrics.containsLine("gen_ai_client_estimated_cost_total"));
        assertTrue(metrics.containsLine("gen_ai_client_operation_duration_seconds_max"));
        assertTrue(metrics.containsLine("gen_ai_client_operation_duration_seconds_count"));
        assertTrue(metrics.containsLine("gen_ai_client_operation_duration_seconds_sum"));
        assertTrue(metrics
                .containsLine(line -> {
                    boolean first = line.startsWith("gen_ai_client_token_usage_total");
                    boolean second = line.contains("""
                            gen_ai_token_type="input"
                            """.trim());
                    return first && second;
                }),"There is no metric for input tokens!");
        String outputTokensMetric = metrics.getLine(line -> {
                    boolean first = line.startsWith("gen_ai_client_token_usage_total");
                    boolean second = line.contains("""
                            gen_ai_token_type="output"
                            """.trim());
                    boolean third = line.contains("Bot");
                    return first && second && third;
                })
                .orElseThrow(() -> new AssertionError("There is no metric for output tokens!"));
        assertTrue(outputTokensMetric.contains("ai_service_class_name=\"io.quarkiverse.langchain4j.sample.chatbot.Bot\""));
        assertTrue(outputTokensMetric.contains("gen_ai_request_model=\"gpt-4o-mini\""));
        String tokens = outputTokensMetric.split("} ")[1];
        Assertions.assertEquals("1.0", tokens, "Wrong number of tokens!");

    }

    class WebSocketListener implements WebSocket.Listener {
        private final List<String> answers;
        private StringBuilder current;

        WebSocketListener(List<String> answers) {
            this.answers = answers;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            LOG.info("Message: " + data);
            if (current == null) {
                current = new StringBuilder();
            }
            current.append(data);
            if (last) {
                answers.add(current.toString());
                current = null;
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }
    }

    record LinesArray(List<String> lines) {
        LinesArray(List<String> lines) {
            this.lines = lines;
        }

        boolean containsLine(String prefix) {
            return this.getLine(line -> line.startsWith(prefix)).isPresent();
        }

        boolean containsLine(Predicate<? super String> condition) {
            return this.getLine(condition).isPresent();
        }

        Optional<String> getLine(Predicate<? super String> condition) {
            return lines.stream().filter(condition).findAny();
        }

    }
}
