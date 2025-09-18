package io.quarkiverse.langchain4j.gpullama3.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ImplicitlyUserConfiguredChatProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.gpullama3.runtime.GpuLlama3Recorder;
import io.quarkiverse.langchain4j.gpullama3.runtime.config.GpuLlama3Config;
import io.quarkiverse.langchain4j.gpullama3.runtime.config.GpuLlama3FixedRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem.ExtendedBeanConfigurator;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;

public class GpuLlama3Processor {

    private static final String PROVIDER = "gpu-llama3";

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer) {
        chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
    }

    @BuildStep
    public void implicitlyConfiguredProviders(BuildProducer<ImplicitlyUserConfiguredChatProviderBuildItem> producer) {
        producer.produce(new ImplicitlyUserConfiguredChatProviderBuildItem(NamedConfigUtil.DEFAULT_NAME, PROVIDER));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(GpuLlama3Recorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChatModels,
            GpuLlama3Config runtimeConfig,
            GpuLlama3FixedRuntimeConfig fixedRuntimeConfig,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (var selected : selectedChatModels) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();

                ExtendedBeanConfigurator builder = SyntheticBeanBuildItem
                        .configure(CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .addInjectionPoint(ParameterizedType.create(
                                DotNames.CDI_INSTANCE,
                                new Type[] { ClassType.create(DotNames.CHAT_MODEL_LISTENER) }, null))
                        .supplier(recorder.chatModel(runtimeConfig, fixedRuntimeConfig, configName));

                beanProducer.produce(builder.done());
            }
        }
    }
}
