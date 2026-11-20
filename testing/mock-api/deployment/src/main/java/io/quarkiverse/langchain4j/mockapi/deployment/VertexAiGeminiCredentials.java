package io.quarkiverse.langchain4j.mockapi.deployment;

import java.util.List;

import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;

import io.quarkiverse.langchain4j.mockapi.internal.MockVertexAiGeminiAuthProviderCreator;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.deployment.annotations.BuildProducer;

/**
 * Vertex AI Gemini authenticates with a Google access token from core's {@code ModelAuthProvider}, resolved with
 * {@code ModelAuthProvider.resolve(configName)}: a bean qualified {@code @ModelName(configName)} first, then an
 * unqualified one, then Google Application Default Credentials. The mock registers a synthetic bean qualified
 * {@code @ModelName(configName)} for every Vertex AI Gemini model in use whose config name no application bean answers,
 * so an application's own provider, qualified for the model or unqualified, is used exactly as without the mock, and
 * the client never falls back to Google credentials. The bean is not left unqualified, because the other providers that
 * honour {@code ModelAuthProvider} would then see it for every model (Anthropic even drops its API key); a model of
 * another provider that shares a config name with a Vertex AI Gemini model still sees it, which is harmless against the
 * mock.
 */
final class VertexAiGeminiCredentials {

    private static final DotName MODEL_AUTH_PROVIDER = DotName
            .createSimple("io.quarkiverse.langchain4j.auth.ModelAuthProvider");
    private static final DotName MODEL_NAME = DotName.createSimple("io.quarkiverse.langchain4j.ModelName");
    private static final DotName MOCK_AUTH_PROVIDER = DotName
            .createSimple("io.quarkiverse.langchain4j.mockapi.internal.MockVertexAiGeminiAuthProvider");

    private VertexAiGeminiCredentials() {
    }

    static void register(List<String> configNames, BeanDiscoveryFinishedBuildItem beans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        List<BeanInfo> applicationProviders = beans.beanStream().withBeanType(MODEL_AUTH_PROVIDER).collect();
        for (String configName : configNames) {
            if (applicationProviders.stream().noneMatch(bean -> answers(bean, configName))) {
                syntheticBeans.produce(SyntheticBeanBuildItem.configure(MOCK_AUTH_PROVIDER)
                        .addType(MODEL_AUTH_PROVIDER)
                        .addQualifier(AnnotationInstance.builder(MODEL_NAME).add("value", configName).build())
                        .defaultBean()
                        .scope(Singleton.class)
                        .unremovable()
                        .creator(MockVertexAiGeminiAuthProviderCreator.class)
                        .done());
            }
        }
    }

    // what ModelAuthProvider.resolve(configName) finds: a bean qualified with that config name, else an unqualified one
    private static boolean answers(BeanInfo bean, String configName) {
        return bean.hasDefaultQualifiers() || bean.getQualifier(MODEL_NAME)
                .map(qualifier -> configName.equals(qualifier.value().asString()))
                .orElse(false);
    }
}
