package io.quarkiverse.langchain4j.sample.registry.agent;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TextPart;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.A2AAgentCardPublisher;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.config.A2AApicurioRegistryRuntimeConfig;
import io.quarkus.runtime.StartupEvent;

/** The same server runs twice, with a different name and instruction in each process. */
@ApplicationScoped
public class TextAgent {

    @Inject
    ChatModel model;

    @Inject
    A2AApicurioRegistryRuntimeConfig registryConfig;

    @ConfigProperty(name = "agent.name")
    String name;

    @ConfigProperty(name = "agent.description")
    String description;

    @ConfigProperty(name = "agent.instruction")
    String instruction;

    void publish(@Observes StartupEvent event) {
        // Use the extension's publisher (including its republish behavior), not a second implementation.
        // Programmatic construction permits two named instances of this one sample application.
        RegistryClient client = RegistryClientFactory.create(RegistryClientOptions
                .create(registryConfig.url().orElseThrow()).httpAdapter(HttpAdapterType.JDK));
        new A2AAgentCardPublisher(client, "default", name, description,
                registryConfig.agentUrl().orElseThrow(), "1.0.0",
                List.of(new A2AAgentCardPublisher.SkillInfo(name, name, description))).publish();
    }

    @Produces
    @PublicAgentCard
    AgentCard card() {
        String url = registryConfig.agentUrl().orElseThrow();
        return AgentCard.builder()
                .name(name).description(description).version("1.0.0")
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", url)))
                .capabilities(AgentCapabilities.builder().streaming(false).pushNotifications(false).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text"))
                .skills(List.of(AgentSkill.builder().id(name).name(name).description(description)
                        .tags(List.of("text")).build()))
                .build();
    }

    @Produces
    AgentExecutor executor() {
        return new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter emitter) {
                if (context.getTask() == null) {
                    emitter.submit();
                }
                emitter.startWork();
                String output = model.chat(SystemMessage.from(instruction), UserMessage.from(context.getUserInput()))
                        .aiMessage().text();
                emitter.addArtifact(List.of(new TextPart(output)), null, null, null);
                emitter.complete();
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) {
                emitter.cancel();
            }
        };
    }
}
