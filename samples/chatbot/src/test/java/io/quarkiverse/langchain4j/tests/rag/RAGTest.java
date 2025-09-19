package io.quarkiverse.langchain4j.tests.rag;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import io.quarkus.websockets.next.WebSocketClientConnection;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@QuarkusTest
public class RAGTest {
    private static final Logger LOG = Logger.getLogger(RAGTest.class);

    @Inject
    BasicWebSocketConnector connector;

    private List<String> answers;

    private WebSocketClientConnection connection;

    @BeforeEach
    void setUp() throws URISyntaxException {
        String host = ConfigProvider.getConfig().getValue("quarkus.http.host", String.class);
        Integer port = getPort();
        URI uri = new URI("ws", null, host, port, null, null, null);
        String socket = "/chatbot";
        LOG.info("Connecting to: " + socket + " at " + uri);
        answers = Collections.synchronizedList(new ArrayList<>());
        connection = connector
                .baseUri(uri)
                .path(socket)
                .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                .onTextMessage((ignoredConnection, message) -> {
                    LOG.info("Message: " + message);
                    answers.add(message);
                })
                .connectAndAwait();
    }

    Integer getPort() {
        return ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
    }

    @AfterEach
    void tearDown() {
        answers = null;
        connection.closeAndAwait();
    }

    @Test
    public void smoke() {
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Assertions.assertFalse(answers.isEmpty());
        });
        Assertions.assertEquals("Hello, I'm Bob, how can I help you?", answers.get(0));
    }

    @Test
    public void documentBasedAnswer() {
        // Input tokens are usually much cheaper, than output tokens, so let's stop the LLM from talking too much
        String prompt = "What is the opening deposit (in USD) for a standard savings account? Answer with number only";
        connection.sendTextAndAwait(prompt);
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Assertions.assertNotEquals(1, answers.size());
        });
        String response = String.join("", answers);// the answer is split to many messages due to Multi<String> in the Bot
        Assertions.assertTrue(response.contains("25"));
    }
}
