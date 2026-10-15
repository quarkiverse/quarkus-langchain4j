package io.quarkiverse.langchain4j.workload.runtime;

import java.util.Optional;
import java.util.function.Function;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.spiffe.client.SpiffeClient;
import io.quarkus.spiffe.svid.jwt.JwtSvidClient;
import io.vertx.core.Vertx;

@Recorder
public class WorkloadModelAuthProviderRecorder {

    private static final String SUBJECT_TOKEN = "subject_token";
    private static final String ASSERTION = "assertion";

    private static final TypeLiteral<Instance<SpiffeClient>> SPIFFE_CLIENT_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<WorkloadModelAuthProviderConfig> config;

    public WorkloadModelAuthProviderRecorder(RuntimeValue<WorkloadModelAuthProviderConfig> config) {
        this.config = config;
    }

    public Function<SyntheticCreationalContext<ModelAuthProvider>, ModelAuthProvider> defaultProvider() {
        return context -> {
            String tokenParamName = deriveTokenParamName("quarkus.oidc-client.grant.type");
            Optional<String> tokenPath = config.getValue().tokenPath();
            return new WorkloadModelAuthProvider(
                    context.getInjectedReference(Vertx.class),
                    context.getInjectedReference(OidcClient.class),
                    tokenPath.orElse(null),
                    tokenPath.isPresent() ? null : resolveSpiffeClient(context),
                    tokenParamName);
        };
    }

    private static JwtSvidClient resolveSpiffeClient(SyntheticCreationalContext<ModelAuthProvider> context) {
        Instance<SpiffeClient> spiffeClient = context.getInjectedReference(SPIFFE_CLIENT_TYPE_LITERAL);
        if (!spiffeClient.isResolvable()) {
            throw new ConfigurationException(
                    "'quarkus.langchain4j.workload-model-auth-provider.token-path' is not set and no SpiffeClient"
                            + " is available. Either set the token path, or add the 'io.quarkus:quarkus-spiffe-client'"
                            + " dependency to request a SPIFFE JWT-SVID from the SPIFFE Workload API.");
        }
        return spiffeClient.get();
    }

    private static String deriveTokenParamName(String configKey) {
        String grantType = ConfigProvider.getConfig()
                .getOptionalValue(configKey, String.class)
                .orElse("client");
        return "jwt".equals(grantType) ? ASSERTION : SUBJECT_TOKEN;
    }
}
