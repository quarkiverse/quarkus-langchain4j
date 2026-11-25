package io.quarkiverse.langchain4j.memorystore.hibernate.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.hibernate.runtime.model.ChatMemorySnapshot;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.test.QuarkusUnitTest;

class NamedPersistenceUnitTest {
    @RegisterExtension
    static final QuarkusUnitTest app = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(ApplicationEntity.class, MessageFixtures.class))
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:unselected;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.hibernate-orm.packages", ApplicationEntity.class.getPackageName())
            .overrideConfigKey("quarkus.hibernate-orm.schema-management.strategy", "drop-and-create")
            .overrideConfigKey("quarkus.datasource.memory.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.memory.jdbc.url", "jdbc:h2:mem:selected;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.hibernate-orm.memory.datasource", "memory")
            .overrideConfigKey("quarkus.hibernate-orm.memory.packages", ChatMemorySnapshot.class.getPackageName())
            .overrideConfigKey("quarkus.hibernate-orm.memory.schema-management.strategy", "drop-and-create")
            .overrideConfigKey("quarkus.langchain4j.memorystore.hibernate.persistence-unit-name", "memory");

    @Inject
    ChatMemoryStore store;

    @Inject
    SessionFactory defaultFactory;

    @Inject
    @PersistenceUnit("memory")
    SessionFactory memoryFactory;

    @Test
    void mapsSnapshotOnlyToSelectedUnit() {
        assertThat(defaultFactory.getMetamodel().getEntities()).noneMatch(e -> e.getJavaType() == ChatMemorySnapshot.class);
        assertThat(memoryFactory.getMetamodel().entity(ChatMemorySnapshot.class)).isNotNull();
        store.updateMessages("named", MessageFixtures.messages());
        assertThat(store.getMessages("named")).containsExactlyElementsOf(MessageFixtures.messages());
        ChatMemorySnapshot snapshot = memoryFactory.fromTransaction(s -> s.find(ChatMemorySnapshot.class, "named"));
        assertThat(snapshot).isNotNull();
        store.deleteMessages("named");
        assertThat(store.getMessages("named")).isEmpty();
    }
}
