package io.quarkiverse.langchain4j.prompt.deployment.apicurio;

import java.lang.reflect.Modifier;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.prompt.runtime.apicurio.ApicurioPromptTemplateRegistry;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.ApicurioSystemMessageProvider;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.PromptApicurioRegistryRecorder;
import io.quarkiverse.langchain4j.prompt.runtime.apicurio.config.PromptApicurioRegistryBuildTimeConfig;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

public class PromptApicurioRegistryProcessor {

    private static final Logger log = Logger.getLogger(PromptApicurioRegistryProcessor.class);
    private static final String FEATURE = "langchain4j-prompt-apicurio-registry";

    private static final DotName APICURIO_PROMPT_TEMPLATE_REGISTRY = DotName.createSimple(ApicurioPromptTemplateRegistry.class);
    private static final DotName APICURIO_SYSTEM_MESSAGE_PROVIDER = DotName.createSimple(ApicurioSystemMessageProvider.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void registerPromptTemplateRegistry(
            PromptApicurioRegistryBuildTimeConfig buildTimeConfig,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            PromptApicurioRegistryRecorder recorder) {
        if (!buildTimeConfig.enabled()) {
            return;
        }

        log.debug("Apicurio Registry prompt template integration enabled, registering ApicurioPromptTemplateRegistry bean");
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(APICURIO_PROMPT_TEMPLATE_REGISTRY)
                .types(ApicurioPromptTemplateRegistry.class)
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .createWith(recorder.registryFunction())
                .done());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    public void validatePromptTemplatesOnStartup(
            PromptApicurioRegistryBuildTimeConfig buildTimeConfig,
            CombinedIndexBuildItem combinedIndex,
            PromptApicurioRegistryRecorder recorder) {
        if (!buildTimeConfig.enabled()) {
            return;
        }
        List<String> providers = providerClassNames(combinedIndex);
        if (providers.isEmpty()) {
            return;
        }
        log.debugf("Registering startup validation for %d prompt template(s) loaded from Apicurio Registry",
                providers.size());
        recorder.validateOnStartup(providers);
    }

    @BuildStep
    public void reflectionRegistrations(
            PromptApicurioRegistryBuildTimeConfig buildTimeConfig,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        if (!buildTimeConfig.enabled()) {
            return;
        }
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "io.apicurio.registry.rest.client.models.VersionMetaData",
                "io.apicurio.registry.rest.client.models.ArtifactMetaData",
                "io.apicurio.registry.rest.client.models.Labels",
                "io.apicurio.registry.rest.client.models.VersionState",
                "io.apicurio.registry.rest.client.models.ProblemDetails")
                .fields(true).methods(true).build());

        List<String> providers = providerClassNames(combinedIndex);
        if (!providers.isEmpty()) {
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(providers.toArray(new String[0]))
                    .constructors(true).build());
        }
    }

    private static List<String> providerClassNames(CombinedIndexBuildItem combinedIndex) {
        return combinedIndex.getIndex().getAllKnownSubclasses(APICURIO_SYSTEM_MESSAGE_PROVIDER).stream()
                .filter(c -> !Modifier.isAbstract(c.flags()) && !c.isInterface())
                .map(ClassInfo::name)
                .map(DotName::toString)
                .sorted()
                .toList();
    }
}
