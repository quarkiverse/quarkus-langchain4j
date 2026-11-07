package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.config.A2AApicurioRegistryRuntimeConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;

@Recorder
public class A2AApicurioRegistryRecorder {

    private static final String CONFIG_PREFIX = "quarkus.langchain4j.a2a.apicurio-registry";

    private final RuntimeValue<A2AApicurioRegistryRuntimeConfig> runtimeConfig;

    public A2AApicurioRegistryRecorder(RuntimeValue<A2AApicurioRegistryRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<ApicurioAgentsRegistry>, ApicurioAgentsRegistry> agentsRegistryFunction() {
        return new Function<>() {
            @Override
            public ApicurioAgentsRegistry apply(SyntheticCreationalContext<ApicurioAgentsRegistry> context) {
                A2AApicurioRegistryRuntimeConfig config = runtimeConfig.getValue();
                RegistryClient registryClient = createRegistryClient(config);
                return new ApicurioAgentsRegistry(registryClient, config.defaultInputKeys());
            }
        };
    }

    public void publishAgentCard(
            String agentName, String agentDescription, String agentVersion,
            List<A2AAgentCardPublisher.SkillInfo> skills) {
        A2AApicurioRegistryRuntimeConfig config = runtimeConfig.getValue();
        RegistryClient registryClient = createRegistryClient(config);
        A2AAgentCardPublisher publisher = new A2AAgentCardPublisher(
                registryClient,
                config.groupId(),
                agentName,
                agentDescription,
                config.agentUrl().orElse("http://localhost:8080"),
                agentVersion,
                skills);
        publisher.publish();
    }

    private static RegistryClient createRegistryClient(A2AApicurioRegistryRuntimeConfig config) {
        String url = config.url().orElseThrow(() -> new ConfigurationException(
                "quarkus.langchain4j.a2a.apicurio-registry.url must be set when the A2A Apicurio Registry integration is enabled"));
        RegistryClientOptions options = RegistryClientOptions.create(url).httpAdapter(HttpAdapterType.JDK);
        if (config.authToken().isPresent()) {
            // Never applied before, so a configured token was silently ignored. The registry SDK that
            // applications get from the Quarkus platform has no static token support.
            throw new ConfigurationException(CONFIG_PREFIX + ".auth-token is not supported. Use "
                    + CONFIG_PREFIX + ".auth.client-id with token-endpoint and client-secret, which"
                    + " fetches and refreshes tokens, or " + CONFIG_PREFIX + ".auth.username with password.",
                    Set.of(CONFIG_PREFIX + ".auth-token"));
        }
        RegistryClientAuth.apply(options, config.auth(), CONFIG_PREFIX);
        return RegistryClientFactory.create(options);
    }
}
