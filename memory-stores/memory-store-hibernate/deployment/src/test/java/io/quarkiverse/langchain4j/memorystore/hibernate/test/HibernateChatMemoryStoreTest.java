package io.quarkiverse.langchain4j.memorystore.hibernate.test;

import static io.quarkiverse.langchain4j.memorystore.hibernate.test.MessageFixtures.messages;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostLoadEventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.memorystore.HibernateChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.hibernate.runtime.model.ChatMemorySnapshot;
import io.quarkus.test.QuarkusUnitTest;

class HibernateChatMemoryStoreTest {
    @RegisterExtension
    static final QuarkusUnitTest app = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClass(MessageFixtures.class))
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:storage-tests;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create");

    private SessionFactory open(String url, String schemaAction) {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.hbm2ddl.auto", schemaAction)
                .applySetting("hibernate.show_sql", false)
                .build();
        try {
            return new MetadataSources(registry).addAnnotatedClass(ChatMemorySnapshot.class)
                    .buildMetadata().buildSessionFactory();
        } catch (RuntimeException | Error failure) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw failure;
        }
    }

    private SessionFactory fresh() {
        return open("jdbc:h2:mem:" + UUID.randomUUID(), "create-drop");
    }

    @Test
    void roundTripsOrderThinkingToolIdsAndMultimodalResults() {
        try (var factory = fresh()) {
            var store = new HibernateChatMemoryStore(factory);
            store.updateMessages("conversation", messages());
            List<ChatMessage> loaded = store.getMessages("conversation");
            assertEquals(messages(), loaded);
            assertEquals("compare alternatives", ((AiMessage) loaded.get(2)).thinking());
            assertEquals("call-1", ((ToolExecutionResultMessage) loaded.get(3)).id());
            assertTrue(((ToolExecutionResultMessage) loaded.get(3)).isError());
        }
    }

    @Test
    void replacementRetainsOneRowAndKeepsOtherConversation() {
        try (var factory = fresh()) {
            var store = new HibernateChatMemoryStore(factory);
            store.updateMessages("first", messages());
            store.updateMessages("second", List.of(UserMessage.from("other")));
            store.updateMessages("first", List.of(AiMessage.from("replacement")));
            assertEquals(List.of(AiMessage.from("replacement")), store.getMessages("first"));
            assertEquals(List.of(UserMessage.from("other")), store.getMessages("second"));
            long rows = factory.fromTransaction(s -> s.createSelectionQuery(
                    "select count(s) from LangChain4jChatMemorySnapshot s", Long.class).getSingleResult());
            assertEquals(2L, rows);
        }
    }

    @Test
    void explicitDeletionAndMissingDeletionAreIdempotent() {
        try (var factory = fresh()) {
            var store = new HibernateChatMemoryStore(factory);
            store.updateMessages("first", messages());
            store.updateMessages("second", messages());
            store.deleteMessages("first");
            store.deleteMessages("first");
            assertEquals(List.of(), store.getMessages("first"));
            assertEquals(messages(), store.getMessages("second"));
        }
    }

    @Test
    void emptySnapshotAndUnknownMemoryReturnEmpty() {
        try (var factory = fresh()) {
            var store = new HibernateChatMemoryStore(factory);
            assertEquals(List.of(), store.getMessages("unknown"));
            store.updateMessages("empty", List.of());
            assertEquals(List.of(), store.getMessages("empty"));
        }
    }

    @Test
    void largeUnicodePayloadIsNotTruncated() {
        try (var factory = fresh()) {
            var store = new HibernateChatMemoryStore(factory);
            var large = List.<ChatMessage> of(UserMessage.from("中文😀".repeat(25000)));
            store.updateMessages(UUID.fromString("1251fe32-c0c7-4fe2-a7bd-a8105267faf5"), large);
            assertEquals(large, store.getMessages("1251fe32-c0c7-4fe2-a7bd-a8105267faf5"));
        }
    }

    @Test
    void fileDatabaseSurvivesFactoryRecreationAndExplicitDeleteStillWorks(@TempDir Path directory) {
        String url = "jdbc:h2:file:" + directory.resolve("memory").toAbsolutePath();
        try (var factory = open(url, "update")) {
            new HibernateChatMemoryStore(factory).updateMessages("restart", messages());
        }
        try (var factory = open(url, "validate")) {
            var store = new HibernateChatMemoryStore(factory);
            assertEquals(messages(), store.getMessages("restart"));
            store.deleteMessages("restart");
        }
        try (var factory = open(url, "validate")) {
            assertEquals(List.of(), new HibernateChatMemoryStore(factory).getMessages("restart"));
        }
    }

    @Test
    void databaseConstraintFailureRollsBackReplacement() {
        try (var factory = fresh()) {
            var store = new HibernateChatMemoryStore(factory);
            var initial = List.<ChatMessage> of(UserMessage.from("short"));
            store.updateMessages("first", initial);
            factory.inTransaction(s -> s.createNativeMutationQuery(
                    "alter table langchain4j_chat_memory add constraint payload_limit check (length(messages_json) < 500)")
                    .executeUpdate());
            assertThrows(RuntimeException.class,
                    () -> store.updateMessages("first", List.of(UserMessage.from("long".repeat(1000)))));
            assertEquals(initial, store.getMessages("first"));
        }
    }

    @Test
    void rejectsInvalidKeysAndNullMessagesBeforeDatabaseMutation() {
        try (var factory = fresh()) {
            var store = new HibernateChatMemoryStore(factory);
            assertThrows(NullPointerException.class, () -> store.getMessages(null));
            assertThrows(IllegalArgumentException.class, () -> store.updateMessages("", messages()));
            assertThrows(IllegalArgumentException.class, () -> store.updateMessages("a".repeat(256), messages()));
            assertThrows(NullPointerException.class, () -> store.updateMessages("first", null));
            assertEquals(List.of(), store.getMessages("first"));
        }
    }

    @Test
    void overlappingDifferentIdsStayIsolated() throws Exception {
        try (var factory = fresh()) {
            var store = new HibernateChatMemoryStore(factory);
            var pool = Executors.newFixedThreadPool(2);
            var ready = new CountDownLatch(2);
            var release = new CountDownLatch(1);
            try {
                Future<?> first = pool.submit(() -> {
                    ready.countDown();
                    await(release);
                    store.updateMessages("first", List.of(UserMessage.from("first")));
                });
                Future<?> second = pool.submit(() -> {
                    ready.countDown();
                    await(release);
                    store.updateMessages("second", List.of(UserMessage.from("second")));
                });
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
                assertEquals(List.of(UserMessage.from("first")), store.getMessages("first"));
                assertEquals(List.of(UserMessage.from("second")), store.getMessages("second"));
            } finally {
                release.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void overlappingSameRowUpdatesRejectOneStaleTransaction() throws Exception {
        try (var factory = fresh()) {
            var store = new HibernateChatMemoryStore(factory);
            store.updateMessages("same", List.of(UserMessage.from("initial")));
            var bothLoaded = new CountDownLatch(2);
            var release = new CountDownLatch(1);
            var registry = factory.unwrap(SessionFactoryImplementor.class).getServiceRegistry()
                    .getService(EventListenerRegistry.class);
            PostLoadEventListener listener = event -> {
                if (event.getEntity() instanceof ChatMemorySnapshot) {
                    bothLoaded.countDown();
                    await(release);
                }
            };
            registry.appendListeners(EventType.POST_LOAD, listener);
            var pool = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> first = pool.submit(() -> updateSucceeded(store, "first"));
                Future<Boolean> second = pool.submit(() -> updateSucceeded(store, "second"));
                assertTrue(bothLoaded.await(5, TimeUnit.SECONDS));
                release.countDown();
                assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
                registry.getEventListenerGroup(EventType.POST_LOAD).clear();
                var result = store.getMessages("same");
                assertTrue(result.equals(List.of(UserMessage.from("first")))
                        || result.equals(List.of(UserMessage.from("second"))));
            } finally {
                release.countDown();
                registry.getEventListenerGroup(EventType.POST_LOAD).clear();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    private static boolean updateSucceeded(HibernateChatMemoryStore store, String text) {
        try {
            store.updateMessages("same", List.of(UserMessage.from(text)));
            return true;
        } catch (jakarta.persistence.OptimisticLockException expected) {
            return false;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("barrier timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
