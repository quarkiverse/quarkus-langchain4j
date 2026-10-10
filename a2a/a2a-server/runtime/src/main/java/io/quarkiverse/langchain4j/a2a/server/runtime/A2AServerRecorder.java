package io.quarkiverse.langchain4j.a2a.server.runtime;

import java.util.List;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentSkill;

import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.DetectedTransports;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class A2AServerRecorder {

    public RuntimeValue<AgentCardBuilderCustomizer> staticInfoCustomizer(AgentCapabilities agentCapabilities,
            List<String> defaultInputModes,
            List<String> defaultOutputModes,
            List<AgentSkill> skills) {
        return new RuntimeValue<>(new AgentCardBuilderCustomizer() {
            // applied before the application's own customizers, so that one of those overrides the annotation
            @Override
            public int priority() {
                return MAXIMUM_PRIORITY;
            }

            @Override
            public void customize(AgentCard.Builder cardBuilder) {
                cardBuilder
                        .capabilities(agentCapabilities)
                        .defaultInputModes(defaultInputModes)
                        .defaultOutputModes(defaultOutputModes)
                        .skills(skills);
            }
        });
    }

    public RuntimeValue<DetectedTransports> detectedTransports(List<String> protocolBindings) {
        return new RuntimeValue<>(new DetectedTransports(protocolBindings));
    }
}
