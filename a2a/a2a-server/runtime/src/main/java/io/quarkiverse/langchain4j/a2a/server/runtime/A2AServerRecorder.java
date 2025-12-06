package io.quarkiverse.langchain4j.a2a.server.runtime;

import java.util.List;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class A2AServerRecorder {

    public RuntimeValue<AgentCardBuilderCustomizer> staticInfoCustomizer(String name, String description,
            AgentCapabilities agentCapabilities,
            List<String> defaultInputModes,
            List<String> defaultOutputModes,
            List<AgentSkill> skills) {
        return new RuntimeValue<>(new AgentCardBuilderCustomizer() {
            @Override
            public void customize(AgentCard.Builder cardBuilder) {
                cardBuilder
                        .name(name)
                        .description(description)
                        .capabilities(agentCapabilities)
                        .defaultInputModes(defaultInputModes)
                        .defaultOutputModes(defaultOutputModes)
                        .skills(skills);
            }
        });
    }
}
