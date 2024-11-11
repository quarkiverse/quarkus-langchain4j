package io.quarkiverse.langchain4j.tavily.deployment;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import dev.langchain4j.web.search.WebSearchEngine;
import io.quarkiverse.langchain4j.tavily.QuarkusTavilyWebSearchEngine;
import io.quarkiverse.langchain4j.tavily.runtime.TavilyConfig;
import io.quarkiverse.langchain4j.tavily.runtime.TavilyRecorder;
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

    /**
     * When both {@code rest-client-jackson} and {@code rest-client-jsonb} are present on the classpath we need to make sure
     * that Jackson is used.
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
