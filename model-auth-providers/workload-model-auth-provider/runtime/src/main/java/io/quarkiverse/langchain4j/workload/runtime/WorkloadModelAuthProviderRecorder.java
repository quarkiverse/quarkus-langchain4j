package io.quarkiverse.langchain4j.workload.runtime;

import java.util.function.Function;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Vertx;

@Recorder
public class WorkloadModelAuthProviderRecorder {

    private static final String SUBJECT_TOKEN = "subject_token";
    private static final String ASSERTION = "assertion";

    private final RuntimeValue<WorkloadModelAuthProviderConfig> config;

    public WorkloadModelAuthProviderRecorder(RuntimeValue<WorkloadModelAuthProviderConfig> config) {
        this.config = config;
    }

    public Function<SyntheticCreationalContext<ModelAuthProvider>, ModelAuthProvider> defaultProvider() {
        return context -> {
            String tokenParamName = deriveTokenParamName("quarkus.oidc-client.grant.type");
            return new WorkloadModelAuthProvider(
                    context.getInjectedReference(Vertx.class),
                    context.getInjectedReference(OidcClient.class),
                    config.getValue().tokenPath(),
                    tokenParamName);
        };
    }

    public Function<SyntheticCreationalContext<ModelAuthProvider>, ModelAuthProvider> namedProvider(
            String oidcClientName) {
        return context -> {
            String tokenParamName = deriveTokenParamName(
                    "quarkus.oidc-client." + oidcClientName + ".grant.type");
            return new WorkloadModelAuthProvider(
                    context.getInjectedReference(Vertx.class),
                    context.getInjectedReference(OidcClients.class).getClient(oidcClientName),
                    config.getValue().tokenPath(),
                    tokenParamName);
        };
    }

    private static String deriveTokenParamName(String configKey) {
        String grantType = ConfigProvider.getConfig()
                .getOptionalValue(configKey, String.class)
                .orElse("client");
        return "jwt".equals(grantType) ? ASSERTION : SUBJECT_TOKEN;
    }
}
