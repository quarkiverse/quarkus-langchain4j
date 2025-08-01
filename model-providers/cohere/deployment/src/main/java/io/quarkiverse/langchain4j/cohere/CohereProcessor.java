package io.quarkiverse.langchain4j.cohere;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.SCORING_MODEL;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;

import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.cohere.runtime.CohereRecorder;
import io.quarkiverse.langchain4j.cohere.runtime.QuarkusCohereScoringModel;
import io.quarkiverse.langchain4j.deployment.items.ScoringModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedScoringModelProviderBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.resteasy.reactive.spi.MessageBodyReaderOverrideBuildItem;
import io.quarkus.resteasy.reactive.spi.MessageBodyWriterOverrideBuildItem;
import io.smallrye.config.Priorities;

public class CohereProcessor {

    static final String FEATURE = "langchain4j-cohere";
    private static final String PROVIDER = "cohere";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ScoringModelProviderCandidateBuildItem> scoringProducer,
            LangChain4jCohereBuildConfig config) {

        if (config.scoringModel().enabled().isEmpty() || config.scoringModel().enabled().get()) {
            scoringProducer.produce(new ScoringModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createScoringModelBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            List<SelectedScoringModelProviderBuildItem> selectedScoring,
            CohereRecorder recorder) {

        for (var selected : selectedScoring) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem
                        .configure(SCORING_MODEL)
                        .types(ClassType.create(ScoringModel.class),
                                ClassType.create(QuarkusCohereScoringModel.class))
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.cohereScoringModelSupplier(configName));
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

    /**
     * When both {@code rest-client-jackson} and {@code rest-client-jsonb} are present on the classpath we need to make sure
     * that the Jackson is used.
     * This is not a proper solution as it affects all clients, but it's better than the having the reader/writers be selected
     * at random.
     */
    @BuildStep
    public void deprioritizeJsonb(Capabilities capabilities,
            BuildProducer<MessageBodyReaderOverrideBuildItem> readerOverrideProducer,
            BuildProducer<MessageBodyWriterOverrideBuildItem> writerOverrideProducer) {
        if (capabilities.isPresent(Capability.REST_CLIENT_REACTIVE_JSONB)) {
            readerOverrideProducer.produce(
                    new MessageBodyReaderOverrideBuildItem("org.jboss.resteasy.reactive.server.jsonb.JsonbMessageBodyReader",
                            Priorities.APPLICATION + 1, true));
            writerOverrideProducer.produce(new MessageBodyWriterOverrideBuildItem(
                    "org.jboss.resteasy.reactive.server.jsonb.JsonbMessageBodyWriter", Priorities.APPLICATION + 1, true));
        }
    }
}
