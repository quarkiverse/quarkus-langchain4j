package io.quarkiverse.langchain4j.deployment;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.IndexView;

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

        String embeddingModel = NamedConfigUtil.DEFAULT_NAME;
        if (cacheBuildConfig.embeddingModel() != null)
            embeddingModel = cacheBuildConfig.embeddingModel().name().orElse(NamedConfigUtil.DEFAULT_NAME);

        aiCacheBuildItemProducer.produce(new AiCacheBuildItem(enableCache, embeddingModel));

        if (enableCache) {
            var configurator = SyntheticBeanBuildItem
                    .configure(AiCacheProvider.class)
                    .setRuntimeInit()
                    .addInjectionPoint(ClassType.create(AiCacheStore.class))
                    .scope(ApplicationScoped.class)
                    .createWith(recorder.messageWindow(cacheConfig))
                    .defaultBean();

            syntheticBeanProducer.produce(configurator.done());
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(AiCacheStore.class));
        }
    }
}
