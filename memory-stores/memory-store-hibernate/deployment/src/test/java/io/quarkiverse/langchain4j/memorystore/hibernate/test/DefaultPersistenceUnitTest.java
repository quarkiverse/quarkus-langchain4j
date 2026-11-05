package io.quarkiverse.langchain4j.memorystore.hibernate.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.hibernate.runtime.model.ChatMemorySnapshot;
import io.quarkus.test.QuarkusUnitTest;

class DefaultPersistenceUnitTest {
    @RegisterExtension
    static final QuarkusUnitTest app = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(MessageFixtures.class, ApplicationEntity.class))
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:default-memory;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create");

    @Inject
    ChatMemoryStore store;

    @Inject
    SessionFactory factory;

    @Inject
    UserTransaction transaction;

    @Inject
    EntityManager entityManager;

    @Test
    void registersEntityAndStoreWithoutApplicationMapping() {
        store.updateMessages("default", MessageFixtures.messages());
        assertThat(store.getMessages("default")).containsExactlyElementsOf(MessageFixtures.messages());
        assertThat(factory.getMetamodel().entity(ChatMemorySnapshot.class)).isNotNull();
        store.deleteMessages("default");
        assertThat(store.getMessages("default")).isEmpty();
    }

    @Test
    void replacesSnapshotAndIsolatesConversationIds() {
        store.updateMessages(1, List.of(UserMessage.from("old")));
        store.updateMessages(2, List.of(UserMessage.from("other")));
        store.updateMessages(1, List.of(UserMessage.from("new")));
        assertThat(store.getMessages("1")).containsExactly(UserMessage.from("new"));
        assertThat(store.getMessages(2)).containsExactly(UserMessage.from("other"));
    }

    @Test
    void snapshotTransactionDoesNotCommitOrJoinCallerTransaction() throws Exception {
        transaction.begin();
        try {
            ApplicationEntity entity = new ApplicationEntity();
            entity.id = 42L;
            entityManager.persist(entity);
            store.updateMessages("outside-caller", List.of(UserMessage.from("durable snapshot")));
            assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        } finally {
            if (transaction.getStatus() == Status.STATUS_ACTIVE) {
                transaction.rollback();
            }
        }
        assertThat(store.getMessages("outside-caller")).containsExactly(UserMessage.from("durable snapshot"));
        ApplicationEntity rolledBack = factory.fromTransaction(session -> session.find(ApplicationEntity.class, 42L));
        assertThat(rolledBack).isNull();

        transaction.begin();
        try {
            store.deleteMessages("outside-caller");
            assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        } finally {
            if (transaction.getStatus() == Status.STATUS_ACTIVE) {
                transaction.rollback();
            }
        }
        assertThat(store.getMessages("outside-caller")).isEmpty();
    }
}
