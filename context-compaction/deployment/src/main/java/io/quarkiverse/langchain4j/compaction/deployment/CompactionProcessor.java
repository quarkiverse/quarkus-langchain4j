package io.quarkiverse.langchain4j.compaction.deployment;

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.compaction.CompactionInspector;
import io.quarkiverse.langchain4j.compaction.CompactionProfile;
import io.quarkiverse.langchain4j.compaction.Compactor;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;
import io.quarkiverse.langchain4j.compaction.runtime.ChatMemoryProviderProducer;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionModelsConfig;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionProfileUsage;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionProfiles;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionRecorder;
import io.quarkiverse.langchain4j.compaction.runtime.DefaultSummarizer;
import io.quarkiverse.langchain4j.compaction.runtime.TokenCounterProducer;
import io.quarkiverse.langchain4j.compaction.strategies.AnchoredSummaryStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.DuplicateCollapseStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.MessageWindowStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.PipelineStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.RelevanceStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.RollingSummaryStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.StrategyFactory;
import io.quarkiverse.langchain4j.compaction.strategies.TimeDecayStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.TokenWindowStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.ToolOutputPruningStrategy;
import io.quarkiverse.langchain4j.deployment.RequestChatModelBeanBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class CompactionProcessor {

    static final DotName COMPACTION_PROFILE = DotName.createSimple(CompactionProfile.class);
    static final DotName REGISTER_AI_SERVICE = DotName.createSimple(RegisterAiService.class);
    static final DotName BEAN_CHAT_MEMORY_PROVIDER_SUPPLIER = DotName
            .createSimple(RegisterAiService.BeanChatMemoryProviderSupplier.class);

    static final String FEATURE = "langchain4j-compaction";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Quarkus LangChain4j only creates model beans for the names requested at build time; the summarizer looks its
     * model up dynamically, so request it explicitly.
     */
    @BuildStep
    RequestChatModelBeanBuildItem requestSummaryModel(CompactionModelsConfig config) {
        return new RequestChatModelBeanBuildItem(config.summary().modelName().orElse(NamedConfigUtil.DEFAULT_NAME));
    }

    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        // infrastructure
                        ChatMemoryProviderProducer.class,
                        CompactionProfiles.class,
                        StrategyFactory.class,
                        CompactionInspector.class,
                        Compactor.class,
                        ToolOutputArchive.class,
                        DefaultSummarizer.class,
                        TokenCounterProducer.class,
                        // built-in strategies
                        MessageWindowStrategy.class,
                        TokenWindowStrategy.class,
                        ToolOutputPruningStrategy.class,
                        DuplicateCollapseStrategy.class,
                        RollingSummaryStrategy.class,
                        AnchoredSummaryStrategy.class,
                        TimeDecayStrategy.class,
                        RelevanceStrategy.class,
                        PipelineStrategy.class)
                .setUnremovable()
                .build();
    }

    /**
     * Collects the AI services annotated with {@code @CompactionProfile}, so the chat memory provider producer knows
     * the profile of each service and {@link CompactionProfiles} can check at startup that every profile is configured.
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    SyntheticBeanBuildItem profileUsage(CombinedIndexBuildItem index, CompactionRecorder recorder) {
        Map<String, String> profiles = new HashMap<>();
        for (AnnotationInstance annotation : index.getIndex().getAnnotations(COMPACTION_PROFILE)) {
            ClassInfo target = annotation.target().asClass();
            AnnotationInstance aiService = target.declaredAnnotation(REGISTER_AI_SERVICE);
            if (aiService == null) {
                throw new IllegalStateException("@CompactionProfile is only supported on @RegisterAiService interfaces: "
                        + target.name());
            }
            AnnotationValue supplier = aiService.value("chatMemoryProviderSupplier");
            if (supplier != null && !supplier.asClass().name().equals(BEAN_CHAT_MEMORY_PROVIDER_SUPPLIER)) {
                throw new IllegalStateException("@CompactionProfile on " + target.name()
                        + " has no effect with a custom chatMemoryProviderSupplier; use"
                        + " CompactionProfiles#provider(String) in the supplier instead");
            }
            profiles.put(target.name().toString(), annotation.value().asString());
        }
        return SyntheticBeanBuildItem.configure(CompactionProfileUsage.class)
                .scope(Singleton.class)
                .supplier(recorder.usage(profiles))
                .unremovable()
                .done();
    }
}
