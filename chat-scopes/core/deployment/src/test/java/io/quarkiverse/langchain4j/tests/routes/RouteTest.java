package io.quarkiverse.langchain4j.tests.routes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.chatscopes.ChatRoute;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteApplicationException;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteConstants;
import io.quarkiverse.langchain4j.chatscopes.ChatRouteContext;
import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.DefaultChatRoute;
import io.quarkiverse.langchain4j.chatscopes.EventType;
import io.quarkiverse.langchain4j.chatscopes.LocalChatRoutes;
import io.quarkiverse.langchain4j.chatscopes.RouteNotFound;
import io.quarkiverse.langchain4j.chatscopes.SystemFailure;
import io.quarkiverse.langchain4j.chatscopes.testutil.MockResult;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import io.vertx.mutiny.core.Promise;

public class RouteTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            SimpleRoute.class,
                            MyChatService.class, Customer.class, MockResult.class, ScopedCounterBean.class,
                            SessionCounterBean.class));

    @ApplicationScoped
    public static class SimpleRoute {

        @Inject
        ChatRouteContext context;

        @ChatRoute("top")
        public String top() {
            ChatScope.push("sub");
            return "top";
        }

        @ChatRoute("sub")
        public String sub() {
            ChatScope.pop();
            return "sub";
        }
    }

    @Inject
    LocalChatRoutes.Client localChatRouter;

    Map<String, List<Object>> chatOnce(String route, String userMessage) {
        return chatOnce(route, userMessage, 5000);
    }

    Map<String, List<Object>> chatOnce(String route, String userMessage, long millis) {
        Map<String, List<Object>> result = new HashMap<>();
        Promise<Void> promise = Promise.promise();
        try (LocalChatRoutes.Session session = localChatRouter.builder().defaultHandler((event, data) -> {
            result.computeIfAbsent(event, k -> new ArrayList<>()).add(data);
            promise.complete();
        }).connect(route)) {
            session.chat(userMessage);
            try {
                promise.future().await().atMost(Duration.ofMillis(millis));
            } catch (Exception e) {

            }
        }
        return result;
    }

    @Test
    public void testSimple() throws Exception {
        Map<String, List<Object>> result = new HashMap<>();
        // use semaphore to make sure test doesn't get ahead handler
        Semaphore semaphore = new Semaphore(0);
        try (LocalChatRoutes.Session session = localChatRouter.builder().defaultHandler((event, data) -> {
            result.computeIfAbsent(event, k -> new ArrayList<>()).add(data);
            semaphore.release();
        }).connect("top")) {
            session.chat("Hi");
            semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
            List messages = result.get(ChatRouteConstants.MESSAGE);
            Assertions.assertEquals(1, result.size());
            Assertions.assertEquals("top", messages.get(0));
            messages.clear();
            session.chat("yo");
            semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
            Assertions.assertEquals(1, result.size());
            Assertions.assertEquals(1, messages.size());
            Assertions.assertEquals("sub", messages.get(0));
            messages.clear();
            session.chat("Hi");
            semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
            Assertions.assertEquals(1, result.size());
            Assertions.assertEquals(1, messages.size());
            Assertions.assertEquals("top", messages.get(0));
        }
    }

    @EventType("customer")
    public record Customer(String name, String email) {

    }

    @ApplicationScoped
    public static class MyChatService {
        @Inject
        ChatRouteContext context;

        @ChatRoute
        @DefaultChatRoute
        public void defaultChat() {
            context.response().message("defaultChat:" + context.request().userMessage());
        }

        @ChatRoute
        public void chatone() {
            context.response().message("one:" + context.request().userMessage());
        }

        @ChatRoute("exception")
        public void exception() {
            throw new RuntimeException("test exception");
        }

        @ChatRoute("application-exception")
        public void applicationException() {
            throw new ChatRouteApplicationException("test application exception");
        }

        @ChatRoute("two")
        public void chatTwo(@MemoryId String memoryId, @UserMessage String userMessage, ChatRouteContext ctx) {
            Assertions.assertNull(memoryId); // There is no handler for @MemoryId. @FrameInject is the default behavior
            Assertions.assertNotNull(userMessage);
            Assertions.assertNotNull(ctx);
            ctx.response().message("two:" + userMessage);
        }

        @ChatRoute("string-result")
        public String stringResult() {
            return "string-result";
        }

        @ChatRoute("null-string")
        public String nullString() {
            return null;
        }

        @ChatRoute("result")
        public Result<String> result() {
            return new MockResult<String>("result");
        }

        @ChatRoute("execution")
        public Result<String> resultWithExecution() {
            MockResult<String> result = new MockResult<String>(null);
            result.addToolResult("result-with-execution");
            return result;
        }

        @ChatRoute("null-result")
        public Result<String> nullResult() {
            MockResult<String> result = new MockResult<String>(null);
            return result;
        }

        @ChatRoute("null-execution")
        public Result<String> nullResultWithExecution() {
            MockResult<String> result = new MockResult<String>(null);
            result.addToolResult(null);
            return result;
        }

        @ChatRoute("event-type")
        @EventType("my-event-type")
        public String eventType() {
            return "event-type";
        }

        @ChatRoute("customer")
        public Customer customer() {
            return new Customer("John Doe", "john.doe@example.com");
        }

        @ChatRoute("customer-event")
        public String customerEvent() {
            Customer c = new Customer("John Doe", "john.doe@example.com");
            context.response().event(c);
            return "customer-event";
        }

        @ChatRoute("multi")
        public Multi<String> multi() {
            // this does not work because CDI scopes are not propagated to raw new threads
            // keep it here for informational purposes
            System.out.println("outside multiuser user message: " + context.request().userMessage());
            return Multi.createFrom().emitter((emitter) -> {
                new Thread(() -> {
                    emit(emitter);
                }).start();
            });
        }

        @Inject
        ManagedExecutor managedExecutor;

        @Inject
        ScopedCounterBean counter;

        @Inject
        SessionCounterBean sessionCounter;

        @ChatRoute("multi-executor")
        public Multi<String> multiExecutor() {
            System.out.println("outside multi user message: " + context.request().userMessage());
            counter.increment();
            System.out.println("outside multi chat scoped counter: " + counter.getCounter());
            sessionCounter.increment();
            System.out.println("outside multi session counter: " + sessionCounter.getCounter());
            return Multi.createFrom().emitter((emitter) -> {
                managedExecutor.execute(() -> {
                    emit(emitter);
                });
            });
        }

        private void emit(MultiEmitter<? super String> emitter) {
            try {
                System.out.println("inside multi user message: " + context.request().userMessage());
                System.out.println("inside multi chat scoped counter: " + counter.getCounter());
                System.out.println("inside multi session counter: " + sessionCounter.getCounter());
                for (int i = 0; i < 5; i++) {
                    emitter.emit(Integer.toString(i));
                    Thread.sleep(1);
                }
                emitter.complete();
            } catch (InterruptedException e) {
                System.err.println("Emitter failed: ");
                e.printStackTrace();
            }
        }

    }

    @ChatScoped
    public static class ScopedCounterBean {
        private int counter = 0;

        public void increment() {
            counter++;
        }

        public int getCounter() {
            return counter;
        }
    }

    @SessionScoped
    public static class SessionCounterBean {
        private int counter = 0;

        public void increment() {
            counter++;
        }

        public int getCounter() {
            return counter;
        }
    }

    void assertMessage(String userMessage, String expectedMessage, String route) {
        Map<String, List<Object>> result = chatOnce(route, userMessage);
        List messages = result.get(ChatRouteConstants.MESSAGE);
        Assertions.assertEquals(1, result.size());
        Assertions.assertNotNull(messages);
        Assertions.assertEquals(expectedMessage, messages.get(0));
    }

    void assertSystemFailure(String userMessage, String route) {
        try {
            chatOnce(route, userMessage);
            Assertions.fail("Expected system failure, but got no exception");
        } catch (SystemFailure e) {
        }
    }

    @Test
    public void testRouteNotFound() {
        try {
            chatOnce("bad route", "Hello, world!");
            Assertions.fail("Expected route not found, but got no exception");
        } catch (RouteNotFound e) {
        }
    }

    @Test
    public void testChat() {
        assertMessage("Hello, world!", "defaultChat:Hello, world!", null);
        assertMessage("Hello, one!", "one:Hello, one!",
                "io.quarkiverse.langchain4j.tests.routes.RouteTest$MyChatService::chatone");
        assertMessage("Hello, two!", "two:Hello, two!", "two");
    }

    void assertNoResult(String userMessage, String route) {
        Map<String, List<Object>> result = chatOnce(route, userMessage, 100);
        Assertions.assertEquals(0, result.size());
    }

    @Test
    public void testResult() {
        assertMessage("Hello, world!", "string-result", "string-result");
        assertMessage("Hello, world!", "result", "result");
        assertMessage("Hello, world!", "result-with-execution", "execution");
    }

    @Test
    public void testNullResult() {
        assertNoResult("Hello, world!", "null-string");
        assertNoResult("Hello, world!", "null-result");
        assertNoResult("Hello, world!", "null-execution");
    }

    @Test
    public void testException() {
        assertSystemFailure("dummy", "exception");
    }

    @Test
    public void testApplicationException() {
        assertNoResult("Hello, world!", "application-exception");
    }

    @Test
    public void testEventType() {
        Map<String, List<Object>> result = chatOnce("event-type", "Hello, world!");
        Assertions.assertEquals(1, result.size());
        List messages = result.get("my-event-type");
        Assertions.assertNotNull(messages);
        Assertions.assertEquals(1, messages.size());
        Assertions.assertEquals("event-type", messages.get(0));

        // test ctx.response().event(Object)
        result = chatOnce("customer-event", "Hello, world!");
        Assertions.assertEquals(2, result.size());
        messages = result.get(ChatRouteConstants.MESSAGE);
        Assertions.assertNotNull(messages);
        Assertions.assertEquals(1, messages.size());
        Assertions.assertEquals("customer-event", messages.get(0));
        messages = result.get("customer");
        Assertions.assertNotNull(messages);
        Assertions.assertEquals(1, messages.size());
        Customer customer = (Customer) messages.get(0);
        Assertions.assertEquals("John Doe", customer.name());
        Assertions.assertEquals("john.doe@example.com", customer.email());
    }

    @Test
    public void testEventTypeOnClass() {
        Map<String, List<Object>> result = chatOnce("customer", "Hello, world!");
        List messages = result.get("customer");
        Assertions.assertNotNull(messages);
        Assertions.assertEquals(1, messages.size());
        Customer customer = (Customer) messages.get(0);
        Assertions.assertEquals("John Doe", customer.name());
        Assertions.assertEquals("john.doe@example.com", customer.email());
    }

    // @Test
    public void testMultiToUniVoid() throws Exception {
        Multi<String> multi = Multi.createFrom().emitter((emitter) -> {
            new Thread(() -> {
                try {
                    for (int i = 0; i < 5; i++) {
                        emitter.emit(Integer.toString(i));
                        Thread.sleep(1000);
                    }
                    emitter.complete();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        });
        Uni<Void> uni = multi.onItem().invoke(item -> System.out.println(item)).collect().last().map(item -> null);
        Thread.sleep(2000);
        System.out.println("Start awaiting on uni");
        uni.await().indefinitely();
        System.out.println("Uni completed");
    }

    @Test
    public void testMultiExecutor() {
        StringBuilder stream = new StringBuilder();
        Promise<Void> promise = Promise.promise();
        try (LocalChatRoutes.Session session = localChatRouter.builder().streamHandler((item) -> {
            stream.append(item);
            System.out.println(item);
            if (stream.toString().equals("01234")) {
                promise.complete();
            }
        }).connect("multi-executor")) {
            session.chat("Hello, world!");
            promise.future().await().atMost(Duration.ofSeconds(5));
        }
        Assertions.assertEquals("01234", stream.toString());
    }

}
