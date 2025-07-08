package io.quarkiverse.langchain4j.oidc.client.mcp.deployment;

import java.util.function.BooleanSupplier;

import io.quarkiverse.langchain4j.oidc.client.mcp.runtime.OidcClientMcpAuthProvider;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.FeatureBuildItem;

@BuildSteps(onlyIf = OidcClientMcpAuthProviderProcessor.IsEnabled.class)
public class OidcClientMcpAuthProviderProcessor {
    private static final String FEATURE = "langchain4j-oidc-client-mcp-auth-provider";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void additionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder().setUnremovable();
        builder.addBeanClass(OidcClientMcpAuthProvider.class);
        additionalBeans.produce(builder.build());
    }

    public static class IsEnabled implements BooleanSupplier {
        OidcClientMcpAuthProviderBuildConfig config;

        public boolean getAsBoolean() {
            return config.enabled().orElse(true);
        }
    }
}
