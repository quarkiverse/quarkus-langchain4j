package io.quarkiverse.langchain4j.compaction.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.compaction.ArchivedToolOutputs;
import io.quarkiverse.langchain4j.compaction.CompactionInspector;
import io.quarkiverse.langchain4j.compaction.CompactionProfile;
import io.quarkiverse.langchain4j.compaction.runtime.ChatMemoryProviderProducer;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionModelsConfig;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionProfileUsage;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionRecorder;
import io.quarkiverse.langchain4j.compaction.runtime.DefaultCompactionProfiles;
import io.quarkiverse.langchain4j.compaction.runtime.DefaultCompactor;
import io.quarkiverse.langchain4j.compaction.runtime.DefaultSummarizer;
import io.quarkiverse.langchain4j.compaction.runtime.InMemoryToolOutputArchive;
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
import io.quarkiverse.langchain4j.deployment.BlockingChatMemoryBuildItem;
import io.quarkiverse.langchain4j.deployment.DeclarativeAiServiceBuildItem;
import io.quarkiverse.langchain4j.deployment.RequestChatModelBeanBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class CompactionProcessor {

    static final DotName COMPACTION_PROFILE = DotName.createSimple(CompactionProfile.class);
    static final DotName REGISTER_AI_SERVICE = DotName.createSimple(RegisterAiService.class);
    static final DotName BEAN_CHAT_MEMORY_PROVIDER_SUPPLIER = DotName
            .createSimple(RegisterAiService.BeanChatMemoryProviderSupplier.class);

    static final DotName CHAT_MEMORY_PROVIDER = DotName.createSimple(ChatMemoryProvider.class);
    static final DotName PRODUCES = DotName.createSimple(Produces.class);
    static final List<DotName> BEAN_DEFINING = List.of(DotName.createSimple(ApplicationScoped.class),
            DotName.createSimple(Singleton.class), DotName.createSimple(Dependent.class),
            DotName.createSimple(RequestScoped.class), DotName.createSimple(SessionScoped.class));

    private static final Logger LOG = Logger.getLogger(CompactionProcessor.class);

    static final String FEATURE = "langchain4j-context-compaction";

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

    /**
     * Compacting chat memories may call a model (summaries, embeddings) when they are read: streaming AI services must
     * not access them on the event loop, even when their tools are non-blocking.
     */
    @BuildStep
    void blockingChatMemory(ChatMemoryOwnershipBuildItem ownership, BuildProducer<BlockingChatMemoryBuildItem> items) {
        if (ownership.managesChatMemory()) {
            items.produce(new BlockingChatMemoryBuildItem("context compaction may call a model when reading the memory"));
        }
    }

    /**
     * The retrieval tool is in the extension: index it so the tool processor finds its {@code @Tool} method when an AI
     * service lists it in its tools.
     */
    @BuildStep
    void retrievalTool(BuildProducer<AdditionalIndexedClassesBuildItem> indexed,
            BuildProducer<AdditionalBeanBuildItem> beans) {
        indexed.produce(new AdditionalIndexedClassesBuildItem(ArchivedToolOutputs.class.getName()));
        beans.produce(AdditionalBeanBuildItem.builder().addBeanClass(ArchivedToolOutputs.class).build());
    }

    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        // infrastructure
                        DefaultCompactionProfiles.class,
                        StrategyFactory.class,
                        CompactionInspector.class,
                        DefaultCompactor.class,
                        InMemoryToolOutputArchive.class,
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
     * Replaces the default chat memory provider of Quarkus LangChain4j, unless the application declares its own
     * {@link ChatMemoryProvider} bean: that bean then wins, and compaction is only available programmatically (see
     * {@code CompactionProfiles#provider(String)}).
     */
    @BuildStep
    ChatMemoryOwnershipBuildItem chatMemoryOwnership(CombinedIndexBuildItem index,
            BuildProducer<AdditionalBeanBuildItem> beans) {
        List<String> applicationProviders = applicationChatMemoryProviders(index.getIndex());
        if (applicationProviders.isEmpty()) {
            beans.produce(AdditionalBeanBuildItem.unremovableOf(ChatMemoryProviderProducer.class));
            return new ChatMemoryOwnershipBuildItem(true);
        }
        LOG.infof("The application declares its own ChatMemoryProvider (%s): context compaction does not replace it."
                + " Use CompactionProfiles#provider(String) to compact the conversations of specific AI services.",
                String.join(", ", applicationProviders));
        return new ChatMemoryOwnershipBuildItem(false);
    }

    /**
     * @return the beans and producers of {@link ChatMemoryProvider} declared outside this extension and LangChain4j
     */
    static List<String> applicationChatMemoryProviders(IndexView index) {
        List<String> result = new ArrayList<>();
        for (ClassInfo implementor : index.getAllKnownImplementors(CHAT_MEMORY_PROVIDER)) {
            if (!isLibraryClass(implementor.name()) && !Modifier.isAbstract(implementor.flags())
                    && !implementor.isInterface() && isBeanClass(implementor)) {
                result.add(implementor.name().toString());
            }
        }
        for (AnnotationInstance produces : index.getAnnotations(PRODUCES)) {
            AnnotationTarget target = produces.target();
            Type type = switch (target.kind()) {
                case METHOD -> target.asMethod().returnType();
                case FIELD -> target.asField().type();
                default -> null;
            };
            if (type != null && type.name().equals(CHAT_MEMORY_PROVIDER)) {
                ClassInfo declaring = target.kind() == AnnotationTarget.Kind.METHOD ? target.asMethod().declaringClass()
                        : target.asField().declaringClass();
                if (!isLibraryClass(declaring.name())) {
                    result.add(declaring.name() + "#" + (target.kind() == AnnotationTarget.Kind.METHOD
                            ? target.asMethod().name()
                            : target.asField().name()));
                }
            }
        }
        return result;
    }

    private static boolean isLibraryClass(DotName name) {
        String n = name.toString();
        // only this extension declares ChatMemoryProvider beans in Quarkus LangChain4j (core uses a synthetic default bean)
        return n.startsWith("io.quarkiverse.langchain4j.compaction.runtime.") || n.startsWith("dev.langchain4j.");
    }

    private static boolean isBeanClass(ClassInfo clazz) {
        for (DotName annotation : BEAN_DEFINING) {
            if (clazz.hasDeclaredAnnotation(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects the AI services annotated with {@code @CompactionProfile}, so the chat memory provider producer knows
     * the profile of each service and {@link DefaultCompactionProfiles} can check at startup that every profile is configured.
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    SyntheticBeanBuildItem profileUsage(CombinedIndexBuildItem index, ChatMemoryOwnershipBuildItem ownership,
            List<DeclarativeAiServiceBuildItem> aiServices, CompactionRecorder recorder) {
        Map<String, String> profiles = new HashMap<>();
        for (AnnotationInstance annotation : index.getIndex().getAnnotations(COMPACTION_PROFILE)) {
            ClassInfo target = annotation.target().asClass();
            AnnotationInstance aiService = target.declaredAnnotation(REGISTER_AI_SERVICE);
            if (aiService == null) {
                throw new IllegalStateException("@CompactionProfile is only supported on @RegisterAiService interfaces: "
                        + target.name());
            }
            if (!ownership.managesChatMemory()) {
                throw new IllegalStateException("@CompactionProfile on " + target.name()
                        + " has no effect because the application declares its own ChatMemoryProvider bean; use"
                        + " CompactionProfiles#provider(String) in that bean instead");
            }
            AnnotationValue supplier = aiService.value("chatMemoryProviderSupplier");
            if (supplier != null && !supplier.asClass().name().equals(BEAN_CHAT_MEMORY_PROVIDER_SUPPLIER)) {
                throw new IllegalStateException("@CompactionProfile on " + target.name()
                        + " has no effect with a custom chatMemoryProviderSupplier; use"
                        + " CompactionProfiles#provider(String) in the supplier instead");
            }
            profiles.put(target.name().toString(), annotation.value().asString());
        }
        Set<String> retrievalToolServices = new HashSet<>();
        DotName retrievalTool = DotName.createSimple(ArchivedToolOutputs.class);
        for (DeclarativeAiServiceBuildItem aiService : aiServices) {
            if (aiService.getToolClassInfos().stream().anyMatch(tool -> tool.name().equals(retrievalTool))) {
                retrievalToolServices.add(aiService.getServiceClassInfo().name().toString());
            }
        }
        return SyntheticBeanBuildItem.configure(CompactionProfileUsage.class)
                .scope(Singleton.class)
                .supplier(recorder.usage(profiles, ownership.managesChatMemory(), retrievalToolServices))
                .unremovable()
                .done();
    }
}
