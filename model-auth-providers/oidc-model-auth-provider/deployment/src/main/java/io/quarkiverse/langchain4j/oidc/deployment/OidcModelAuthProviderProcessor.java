package io.quarkiverse.langchain4j.oidc.deployment;

import java.util.function.BooleanSupplier;

import io.quarkiverse.langchain4j.oidc.runtime.OidcModelAuthProvider;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.FeatureBuildItem;

@BuildSteps(onlyIf = OidcModelAuthProviderProcessor.IsEnabled.class)
public class OidcModelAuthProviderProcessor {
    private static final String FEATURE = "langchain4j-oidc-model-auth-provider";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void additionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder().setUnremovable();
        builder.addBeanClass(OidcModelAuthProvider.class);
        additionalBeans.produce(builder.build());
    }

    public static class IsEnabled implements BooleanSupplier {
        OidcModelAuthProviderBuildConfig config;

        public boolean getAsBoolean() {
            return config.enabled().orElse(true);
        }
    }
}
