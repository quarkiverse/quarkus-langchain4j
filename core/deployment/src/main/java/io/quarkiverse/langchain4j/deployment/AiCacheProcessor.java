package io.quarkiverse.langchain4j.deployment;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.IndexView;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.runtime.AiCacheRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheProvider;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheStore;
import io.quarkiverse.langchain4j.runtime.cache.config.AiCacheConfig;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;

public class AiCacheProcessor {

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void setupBeans(AiCacheBuildConfig cacheBuildConfig,
            AiCacheConfig cacheConfig,
            AiCacheRecorder recorder,
            CombinedIndexBuildItem indexBuildItem,
            BuildProducer<AiCacheBuildItem> aiCacheBuildItemProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableProducer,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer) {

        IndexView index = indexBuildItem.getIndex();
        boolean enableCache = false;

        for (AnnotationInstance instance : index.getAnnotations(LangChain4jDotNames.REGISTER_AI_SERVICES)) {
            if (instance.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }

            ClassInfo declarativeAiServiceClassInfo = instance.target().asClass();

            if (declarativeAiServiceClassInfo.hasAnnotation(LangChain4jDotNames.CACHE_RESULT)) {
                enableCache = true;
                break;
            }
        }

        String embeddingModelName = NamedConfigUtil.DEFAULT_NAME;
        if (cacheBuildConfig.embedding() != null)
            embeddingModelName = cacheBuildConfig.embedding().name().orElse(NamedConfigUtil.DEFAULT_NAME);

        aiCacheBuildItemProducer.produce(new AiCacheBuildItem(enableCache, embeddingModelName));

        if (enableCache) {
            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(AiCacheProvider.class)
                    .setRuntimeInit()
                    .addInjectionPoint(ClassType.create(AiCacheStore.class))
                    .scope(ApplicationScoped.class)
                    .createWith(recorder.messageWindow(cacheConfig, embeddingModelName))
                    .defaultBean();

            if (NamedConfigUtil.isDefault(embeddingModelName)) {
                configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.EMBEDDING_MODEL));
            } else {
                configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.EMBEDDING_MODEL),
                        AnnotationInstance.builder(ModelName.class).add("value", embeddingModelName).build());
            }

            syntheticBeanProducer.produce(configurator.done());
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(AiCacheStore.class));
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(EmbeddingModel.class));
        }
    }
}
