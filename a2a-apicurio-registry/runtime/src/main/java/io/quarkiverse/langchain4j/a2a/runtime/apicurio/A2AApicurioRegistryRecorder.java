package io.quarkiverse.langchain4j.a2a.runtime.apicurio;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.config.A2AApicurioRegistryRuntimeConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;

@Recorder
public class A2AApicurioRegistryRecorder {

    private final RuntimeValue<A2AApicurioRegistryRuntimeConfig> runtimeConfig;

    public A2AApicurioRegistryRecorder(RuntimeValue<A2AApicurioRegistryRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<ApicurioAgentsRegistry>, ApicurioAgentsRegistry> agentsRegistryFunction(
            Supplier<io.vertx.core.Vertx> vertx) {
        return new Function<>() {
            @Override
            public ApicurioAgentsRegistry apply(SyntheticCreationalContext<ApicurioAgentsRegistry> context) {
                A2AApicurioRegistryRuntimeConfig config = runtimeConfig.getValue();
                RegistryClient registryClient = createRegistryClient(config);
                return new ApicurioAgentsRegistry(registryClient);
            }
        };
    }

    public Function<SyntheticCreationalContext<A2AAgentCardPublisher>, A2AAgentCardPublisher> agentCardPublisherFunction(
            Supplier<io.vertx.core.Vertx> vertx,
            String agentName, String agentDescription, String agentVersion,
            List<A2AAgentCardPublisher.SkillInfo> skills) {
        return new Function<>() {
            @Override
            public A2AAgentCardPublisher apply(SyntheticCreationalContext<A2AAgentCardPublisher> context) {
                A2AApicurioRegistryRuntimeConfig config = runtimeConfig.getValue();
                RegistryClient registryClient = createRegistryClient(config);
                return new A2AAgentCardPublisher(
                        registryClient,
                        config.groupId(),
                        agentName,
                        agentDescription,
                        config.agentUrl().orElse("http://localhost:8080"),
                        agentVersion,
                        skills);
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
        RegistryClientOptions options = RegistryClientOptions.create(url);
        return RegistryClientFactory.create(options);
    }
}
