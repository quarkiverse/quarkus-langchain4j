package io.quarkiverse.langchain4j.cohere;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.cohere.runtime.CohereRecorder;
import io.quarkiverse.langchain4j.cohere.runtime.QuarkusCohereScoringModel;
import io.quarkiverse.langchain4j.cohere.runtime.config.CohereConfig;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class CohereProcessor {

    public static final DotName COHERE_SCORING_MODEL = DotName.createSimple(QuarkusCohereScoringModel.class);

    static final String FEATURE = "langchain4j-cohere";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createScoringModelBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            CohereRecorder recorder,
            CohereConfig config) {
        // TODO: maybe add some kind of ScoringModelBuildItem class and produce it here
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(COHERE_SCORING_MODEL)
                .types(ClassType.create(ScoringModel.class),
                        ClassType.create(QuarkusCohereScoringModel.class))
                .defaultBean()
                .setRuntimeInit()
                .defaultBean()
                .scope(ApplicationScoped.class)
                .supplier(recorder.cohereScoringModelSupplier(config))
                .done());
    }

}
