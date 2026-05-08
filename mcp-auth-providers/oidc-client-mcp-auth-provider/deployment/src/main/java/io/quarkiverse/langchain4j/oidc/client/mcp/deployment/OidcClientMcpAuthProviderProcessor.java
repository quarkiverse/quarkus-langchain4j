package io.quarkiverse.langchain4j.oidc.client.mcp.deployment;

import java.util.function.BooleanSupplier;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpBuildTimeConfiguration;
import io.quarkiverse.langchain4j.oidc.client.mcp.runtime.OidcClientMcpAuthProviderRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;

@BuildSteps(onlyIf = OidcClientMcpAuthProviderProcessor.IsEnabled.class)
public class OidcClientMcpAuthProviderProcessor {

    private static final String FEATURE = "langchain4j-oidc-client-mcp-auth-provider";
    private static final DotName MCP_CLIENT_AUTH_PROVIDER = DotName.createSimple(McpClientAuthProvider.class);
    private static final DotName MCP_CLIENT_NAME = DotName.createSimple(McpClientName.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void registerBeans(
            McpBuildTimeConfiguration mcpConfig,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            OidcClientMcpAuthProviderRecorder recorder) {

        beanProducer.produce(SyntheticBeanBuildItem
                .configure(MCP_CLIENT_AUTH_PROVIDER)
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(DotName.createSimple(OidcClient.class)))
                .createWith(recorder.defaultProvider())
                .done());

        mcpConfig.clients().forEach((clientName, clientConfig) -> clientConfig.oidcClientName().ifPresent(oidcClientName -> {
            AnnotationInstance qualifier = AnnotationInstance.builder(MCP_CLIENT_NAME)
                    .add("value", clientName)
                    .build();
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(MCP_CLIENT_AUTH_PROVIDER)
                    .addQualifier(qualifier)
                    .setRuntimeInit()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ClassType.create(DotName.createSimple(OidcClients.class)))
                    .createWith(recorder.namedProvider(oidcClientName))
                    .done());
        }));
    }

    public static class IsEnabled implements BooleanSupplier {
        OidcClientMcpAuthProviderBuildConfig config;

        public boolean getAsBoolean() {
            return config.enabled().orElse(true);
        }
    }
}
