package io.quarkiverse.langchain4j.memorystore.hibernate.deployment;

import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.hibernate.SessionFactory;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.HibernateChatMemoryStore;
import io.quarkiverse.langchain4j.memorystore.hibernate.runtime.HibernateMemoryStoreRecorder;
import io.quarkiverse.langchain4j.memorystore.hibernate.runtime.model.ChatMemorySnapshot;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.hibernate.orm.deployment.PersistenceUnitDescriptorBuildItem;
import io.quarkus.hibernate.orm.deployment.spi.AdditionalJpaModelBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class HibernateMemoryStoreProcessor {
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("langchain4j-memory-store-hibernate");
    }

    @BuildStep
    AdditionalJpaModelBuildItem registerEntity(HibernateMemoryStoreBuildTimeConfig config) {
        return new AdditionalJpaModelBuildItem(ChatMemorySnapshot.class.getName(),
                Set.of(config.persistenceUnitName().orElse("<default>")));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    SyntheticBeanBuildItem memoryStore(HibernateMemoryStoreBuildTimeConfig config,
            List<PersistenceUnitDescriptorBuildItem> persistenceUnits, HibernateMemoryStoreRecorder recorder) {
        String unit = config.persistenceUnitName().orElse("<default>");
        if (persistenceUnits.stream().noneMatch(pu -> unit.equals(pu.getPersistenceUnitName()) && !pu.isReactive())) {
            throw new ConfigurationException("No Hibernate ORM persistence unit '" + unit
                    + "' for quarkus.langchain4j.memorystore.hibernate.persistence-unit-name");
        }
        AnnotationInstance qualifier = "<default>".equals(unit)
                ? AnnotationInstance.builder(Default.class).build()
                : AnnotationInstance.builder(PersistenceUnit.class).add("value", unit).build();
        return SyntheticBeanBuildItem.configure(HibernateChatMemoryStore.class)
                .types(ChatMemoryStore.class)
                .scope(ApplicationScoped.class)
                .setRuntimeInit()
                .addInjectionPoint(ClassType.create(SessionFactory.class), qualifier)
                .createWith(recorder.memoryStoreFunction(unit))
                .done();
    }
}
