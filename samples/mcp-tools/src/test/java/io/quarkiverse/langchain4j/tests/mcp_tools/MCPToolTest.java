package io.quarkiverse.langchain4j.tests.mcp_tools;

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
public class MCPToolTest {
    private static final Logger LOG = Logger.getLogger(MCPToolTest.class);

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

    @Test
    public void readFile() {
        String prompt = "Read the contents of the file hello.txt";
        webSocket.sendText(prompt, true);

        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Assertions.assertTrue(answers.size() > 1);
        });
        Assertions.assertTrue(answers.get(0).contains("Hello, I am a filesystem robot, how can I help?"));
        Assertions.assertTrue(answers.get(1).contains("Hello world!"));
    }

    @AfterEach
    void tearDown() throws ExecutionException, InterruptedException {
        answers = null;
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").get();
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
