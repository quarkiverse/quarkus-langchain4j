package io.quarkiverse.langchain4j.skills.deployment;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.runtime.skills.SkillsConfigurator;
import io.quarkiverse.langchain4j.skills.runtime.DefaultSkillsConfigurator;
import io.quarkiverse.langchain4j.skills.runtime.SkillsRecorder;
import io.quarkiverse.langchain4j.skills.runtime.SkillsToolProvider;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class SkillsProcessor {

    private static final String FEATURE = "langchain4j-skills";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void registerSkillsToolProvider(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            SkillsRecorder recorder) {
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(SkillsToolProvider.class)
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .types(SkillsToolProvider.class)
                .supplier(recorder.toolProviderSupplier())
                .done());
    }

    @BuildStep
    void markSkillsConfiguratorUnremovable(BuildProducer<UnremovableBeanBuildItem> unremovable) {
        unremovable.produce(UnremovableBeanBuildItem.beanTypes(DefaultSkillsConfigurator.class, SkillsConfigurator.class));
    }
}
