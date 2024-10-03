package io.quarkiverse.langchain4j.tavily.deployment;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import dev.langchain4j.web.search.WebSearchEngine;
import io.quarkiverse.langchain4j.tavily.QuarkusTavilyWebSearchEngine;
import io.quarkiverse.langchain4j.tavily.runtime.TavilyConfig;
import io.quarkiverse.langchain4j.tavily.runtime.TavilyRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class TavilyProcessor {

    public static final DotName TAVILY_WEB_SEARCH_ENGINE = DotName.createSimple(QuarkusTavilyWebSearchEngine.class);

    static final String FEATURE = "langchain4j-tavily";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void createBean(
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            TavilyRecorder recorder,
            TavilyConfig config) {
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(TAVILY_WEB_SEARCH_ENGINE)
                .types(ClassType.create(WebSearchEngine.class),
                        ClassType.create(QuarkusTavilyWebSearchEngine.class))
                .defaultBean()
                .setRuntimeInit()
                .unremovable()
                .scope(ApplicationScoped.class)
                .supplier(recorder.tavilyEngineSupplier(config))
                .done());
    }
}
