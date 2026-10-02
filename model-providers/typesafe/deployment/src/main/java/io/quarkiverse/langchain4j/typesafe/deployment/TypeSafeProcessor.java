package io.quarkiverse.langchain4j.typesafe.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.DECISION_MODEL;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.DotNames;
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

    private static final String FEATURE = "langchain4j-typesafe";
    private static final String PROVIDER = "typesafe";

    private static final DotName TYPESAFE_DECISION_MODEL_BUILDER = DotName
            .createSimple(TypeSafeDecisionModel.TypeSafeDecisionModelBuilder.class);

    private static final AnnotationInstance ANY = AnnotationInstance.builder(DotName.createSimple(
            Any.class)).build();

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
    void generateBeans(TypeSafeRecorder recorder,
            List<SelectedDecisionModelProviderBuildItem> selectedDecision,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (var selected : selectedDecision) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem
                        .configure(DECISION_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        .scope(ApplicationScoped.class)
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ClassType.create(DotNames.DECISION_MODEL_LISTENER) }, null))
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(TYPESAFE_DECISION_MODEL_BUILDER) }, null) },
                                null), ANY)
                        .createWith(recorder.decisionModel(configName));
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
