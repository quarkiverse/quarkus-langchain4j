package io.quarkiverse.langchain4j.chatscopes.tests;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.chatscopes.ChatRoute;
import io.quarkiverse.langchain4j.chatscopes.websocket.WebsocketChatRoutes;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import io.smallrye.mutiny.Multi;
import io.vertx.mutiny.core.Promise;

public class WebsocketTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(MyChatService.class));

    public static int getTestPort() {
        return ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
    }

    @Inject
    ObjectMapper objectMapper;

    @ApplicationScoped
    public static class MyChatService {

        @ChatRoute("test")
        public String chat(@UserMessage String userMessage) {
            System.out.println("chat: " + userMessage);
            return "Received: " + userMessage;
        }

        @Inject
        ManagedExecutor managedExecutor;

        @ChatRoute("multi")
        public Multi<String> multi() {
            return Multi.createFrom().emitter((emitter) -> {
                managedExecutor.execute(() -> {
                    try {
                        for (int i = 0; i < 5; i++) {
                            emitter.emit(Integer.toString(i));
                            Thread.sleep(100);
                        }
                        emitter.complete();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            });
        }
    }

    @Test
    public void testWebSocket() throws InterruptedException {
        WebsocketChatRoutes.Client client = create();
        // using promise to make sure test doesn't get ahead of messageHandler
        AtomicReference<Promise<String>> promise = new AtomicReference<>();
        WebsocketChatRoutes.Session session = client.builder()
                .messageHandler(msg -> {
                    promise.get().complete(msg);
                })
                .connect("test");
        promise.set(Promise.promise());
        session.chat("Bonjour!");
        Assertions.assertEquals("Received: Bonjour!", promise.get().futureAndAwait());
        promise.set(Promise.promise());
        session.chat("Guten Tag!");
        Assertions.assertEquals("Received: Guten Tag!", promise.get().futureAndAwait());
        promise.set(Promise.promise());
        session.chat();
        Assertions.assertEquals("Received: null", promise.get().futureAndAwait());
        session.close();
        client.close();
    }

    WebsocketChatRoutes.Client create() {
        BasicWebSocketConnector connector = BasicWebSocketConnector.create();
        connector.baseUri("http://localhost:" + getTestPort() + "/_chat/routes");
        return WebsocketChatRoutes.newClient(connector, objectMapper);
    }

    @Test
    public void testMulti() throws InterruptedException {
        System.out.println("***** testMulti");
        WebsocketChatRoutes.Client client = create();
        StringBuilder stream = new StringBuilder();
        Promise<Void> promise = Promise.promise();
        WebsocketChatRoutes.Session session = client.builder()
                .streamHandler(msg -> {
                    stream.append(msg);
                    if (stream.toString().equals("01234")) {
                        promise.complete();
                    }
                    System.out.println(msg);
                })
                .connect("multi");
        session.chat("Hello, world!");
        promise.future().await().atMost(Duration.ofSeconds(5));
        Assertions.assertEquals("01234", stream.toString());
    }

    @Test
    public void testClientClose() throws InterruptedException {
        WebsocketChatRoutes.Client client = create();
        AtomicReference<String> result = new AtomicReference<>();
        WebsocketChatRoutes.Session session = client.builder()
                .messageHandler(msg -> {
                    result.set(msg);
                })
                .connect("test");
        session.chat("Bonjour!");
        client.close();
    }

}
