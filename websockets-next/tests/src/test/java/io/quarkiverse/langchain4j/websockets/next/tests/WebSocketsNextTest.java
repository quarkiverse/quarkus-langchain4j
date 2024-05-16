package io.quarkiverse.langchain4j.websockets.next.tests;

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.awaitility.Awaitility;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;

public class WebSocketsNextTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(RequestScopedChatBot.class, RestChatBot.class,
                            TrackingInMemoryChatMemoryStore.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    TrackingInMemoryChatMemoryStore chatMemoryStore;

    @Inject
    Vertx vertx;

    @TestHTTPResource("websocket")
    URI webSocketUri;

    @BeforeEach
    void setUp() {
        chatMemoryStore.idsFromGetMessages.clear();
        chatMemoryStore.idsFromDeleteMessaged.clear();
    }

    @Test
    public void restRequest() {
        assertThat(chatMemoryStore.idsFromGetMessages).isEmpty();
        assertThat(chatMemoryStore.idsFromDeleteMessaged).isEmpty();

        when()
                .get("rest")
                .then()
                .statusCode(200);

        assertThat(chatMemoryStore.idsFromGetMessages).hasSize(1)
                .hasOnlyElementsOfType(String.class);
        assertThat(chatMemoryStore.idsFromGetMessages).hasSameElementsAs(chatMemoryStore.idsFromDeleteMessaged);

        when()
                .get("rest")
                .then()
                .statusCode(200);

        assertThat(chatMemoryStore.idsFromGetMessages).hasSize(2)
                .hasOnlyElementsOfType(String.class);
        assertThat(chatMemoryStore.idsFromGetMessages).hasSameElementsAs(chatMemoryStore.idsFromDeleteMessaged);
    }

    @Test
    public void webSocketRequest() throws InterruptedException, ExecutionException {
        assertThat(chatMemoryStore.idsFromGetMessages).isEmpty();
        assertThat(chatMemoryStore.idsFromDeleteMessaged).isEmpty();

        // first request will open, receive, send and close

        WebSocketClient client = vertx.createWebSocketClient();
        try {
            CountDownLatch latch = new CountDownLatch(3);
            client
                    .connect(webSocketUri.getPort(), webSocketUri.getHost(), webSocketUri.getPath())
                    .onComplete(connectResult -> {
                        if (connectResult.succeeded()) {
                            latch.countDown();

                            AtomicBoolean firstResponse = new AtomicBoolean(true);

                            io.vertx.core.http.WebSocket webSocket = connectResult.result();
                            webSocket.textMessageHandler((input -> {
                                if (firstResponse.compareAndSet(true, false)) {
                                    // the bot has already chatted, so there should be some memory
                                    assertThat(chatMemoryStore.idsFromGetMessages).hasSize(1)
                                            .hasOnlyElementsOfType(String.class);
                                    assertThat(chatMemoryStore.idsFromDeleteMessaged).isEmpty();

                                    webSocket.writeTextMessage("what is your name?", (firstWriteResult -> {
                                        if (firstWriteResult.succeeded()) {
                                            // the message has been written
                                            assertThat(chatMemoryStore.idsFromGetMessages).hasSize(1)
                                                    .hasOnlyElementsOfType(String.class);
                                            assertThat(chatMemoryStore.idsFromDeleteMessaged).isEmpty();

                                            latch.countDown();

                                            webSocket.close().onComplete((closeResult) -> {
                                                if (closeResult.succeeded()) {
                                                    latch.countDown();
                                                } else {
                                                    throw new IllegalStateException(closeResult.cause());
                                                }
                                            });

                                        } else {
                                            throw new IllegalStateException(firstWriteResult.cause());
                                        }
                                    }));
                                }
                            }));
                        } else {
                            throw new IllegalStateException(connectResult.cause());
                        }
                    });
            assertTrue(latch.await(10, TimeUnit.SECONDS));

            // we have closed the client side, but we need to wait until the server has also closed its side and cleared the memory
            Awaitility.given().pollInterval(100, TimeUnit.MILLISECONDS)
                    .atMost(1, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        assertThat(chatMemoryStore.idsFromGetMessages).hasSize(1)
                                .hasOnlyElementsOfType(String.class);
                        assertThat(chatMemoryStore.idsFromGetMessages).hasSameElementsAs(
                                chatMemoryStore.idsFromDeleteMessaged);
                    });
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }

        // second request  will just open and close
        client = vertx.createWebSocketClient();
        try {
            CountDownLatch latch = new CountDownLatch(3);
            client
                    .connect(webSocketUri.getPort(), webSocketUri.getHost(), webSocketUri.getPath())
                    .onComplete(connectResult -> {
                        if (connectResult.succeeded()) {
                            latch.countDown();

                            io.vertx.core.http.WebSocket webSocket = connectResult.result();

                            webSocket.textMessageHandler((input) -> {
                                latch.countDown();

                                assertThat(chatMemoryStore.idsFromGetMessages).hasSize(2)
                                        .hasOnlyElementsOfType(String.class);
                                assertThat(chatMemoryStore.idsFromDeleteMessaged).hasSize(1);

                                webSocket.close().onComplete((closeResult) -> {
                                    if (closeResult.succeeded()) {
                                        latch.countDown();
                                    } else {
                                        throw new IllegalStateException(closeResult.cause());
                                    }
                                });
                            });
                        }
                    });

            assertTrue(latch.await(10, TimeUnit.SECONDS));

            Awaitility.given().pollInterval(100, TimeUnit.MILLISECONDS)
                    .atMost(1, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        assertThat(chatMemoryStore.idsFromGetMessages).hasSize(2)
                                .hasOnlyElementsOfType(String.class);
                        assertThat(chatMemoryStore.idsFromGetMessages).hasSameElementsAs(
                                chatMemoryStore.idsFromDeleteMessaged);
                    });
        } finally {
            client.close().toCompletionStage().toCompletableFuture().get();
        }

    }

    @RegisterAiService
    public interface RequestScopedChatBot {

        String chat(String message);
    }

    @Path("rest")
    public static class RestChatBot {

        private final RequestScopedChatBot chatBot;

        public RestChatBot(RequestScopedChatBot chatBot) {
            this.chatBot = chatBot;
        }

        @GET
        public String test() {
            return chatBot.chat("what is you name?");
        }
    }

    @RegisterAiService
    @SessionScoped
    public interface SessionScopedChatBot {

        String chat(String message);
    }

    @WebSocket(path = "/websocket")
    public static class WebSocketChatBot {

        private final SessionScopedChatBot bot;

        public WebSocketChatBot(SessionScopedChatBot bot) {
            this.bot = bot;
        }

        @OnOpen
        public String onOpen() {
            return bot.chat("hello");
        }

        @OnTextMessage
        public String onMessage(String message) {
            return bot.chat(message);
        }

    }

    @Singleton
    public static class TrackingInMemoryChatMemoryStore extends InMemoryChatMemoryStore {

        public final Set<Object> idsFromGetMessages = Collections.synchronizedSet(new LinkedHashSet<>());
        public final Set<Object> idsFromDeleteMessaged = Collections.synchronizedSet(new LinkedHashSet<>());

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            idsFromGetMessages.add(memoryId);
            return super.getMessages(memoryId);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            idsFromDeleteMessaged.add(memoryId);
            super.deleteMessages(memoryId);
        }
    }

}
