package io.quarkiverse.langchain4j.tests.rag;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@QuarkusTest
public class RAGTest {
    private static final Logger LOG = Logger.getLogger(RAGTest.class);

    private List<String> answers;
    private WebSocket webSocket;

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
        webSocket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(uri, new WebSocketListener(answers)).get();
    }

    @AfterEach
    void tearDown() throws ExecutionException, InterruptedException {
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
    public void documentBasedAnswer() throws InterruptedException {
        // Input tokens are usually much cheaper, than output tokens, so let's stop the LLM from talking too much
        String prompt = "What is the opening deposit (in USD) for a standard savings account? Answer with number only";
        webSocket.sendText(prompt, true);

        // The answer is split to many messages due to Multi<String> in the Bot.
        // We need to wait for the end of the answer, detected as no new messages during a second.
        // The prompt should protect against this, but it is not guaranteed.
        int repeats = 0;
        int lastSize=0;
        while (answers.size() <= 1 || answers.size()!=lastSize) {
            if (repeats++ > 10) {
                LOG.warn("We have waited for: " + repeats + " seconds and it is too much!");
                break;
            }
            lastSize=answers.size();
            Thread.sleep(1000);
        }

        String response = String.join("", answers);
        Assertions.assertTrue(response.contains("25"));
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
}
