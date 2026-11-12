package io.quarkiverse.langchain4j.memorystore.hibernate.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import org.hibernate.SessionFactory;

import io.quarkiverse.langchain4j.memorystore.HibernateChatMemoryStore;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class HibernateMemoryStoreRecorder {
    public Function<SyntheticCreationalContext<HibernateChatMemoryStore>, HibernateChatMemoryStore> memoryStoreFunction(
            String persistenceUnitName) {
        return context -> new HibernateChatMemoryStore("<default>".equals(persistenceUnitName)
                ? context.getInjectedReference(SessionFactory.class, Default.Literal.INSTANCE)
                : context.getInjectedReference(SessionFactory.class,
                        new PersistenceUnit.PersistenceUnitLiteral(persistenceUnitName)));
    }
}
