package io.quarkiverse.langchain4j.typesafe.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.DECISION_MODEL;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.items.DecisionModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedDecisionModelProviderBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.typesafe.runtime.TypeSafeRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class TypeSafeProcessor {

    static final String FEATURE = "langchain4j-typesafe";
    private static final String PROVIDER = "typesafe";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void providerCandidates(BuildProducer<DecisionModelProviderCandidateBuildItem> decisionProducer,
            LangChain4jTypeSafeBuildConfig config) {

        if (config.decisionModel().enabled().isEmpty() || config.decisionModel().enabled().get()) {
            decisionProducer.produce(new DecisionModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createDecisionModelBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            List<SelectedDecisionModelProviderBuildItem> selectedDecision,
            TypeSafeRecorder recorder) {

        for (var selected : selectedDecision) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem
                        .configure(DECISION_MODEL)
                        .types(ClassType.create(DecisionModel.class),
                                ClassType.create(TypeSafeDecisionModel.class))
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.typeSafeDecisionModelSupplier(configName));
                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());
            }
        }
    }

    private void addQualifierIfNecessary(SyntheticBeanBuildItem.ExtendedBeanConfigurator builder, String configName) {
        if (!NamedConfigUtil.isDefault(configName)) {
            builder.addQualifier(AnnotationInstance.builder(ModelName.class).add("value", configName).build());
        }
    }
}
