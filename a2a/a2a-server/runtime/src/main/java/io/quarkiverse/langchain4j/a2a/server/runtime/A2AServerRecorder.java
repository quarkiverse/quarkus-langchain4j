package io.quarkiverse.langchain4j.a2a.server.runtime;

import java.util.List;
import java.util.function.Function;

import org.a2aproject.sdk.spec.AgentCard;

import io.quarkiverse.langchain4j.a2a.server.runtime.card.AgentCardProducer;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.DetectedTransports;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.ExposedAgent;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class A2AServerRecorder {

    public Function<SyntheticCreationalContext<AgentCard>, AgentCard> agentCard(ExposedAgent agent) {
        return new Function<>() {
            @Override
            public AgentCard apply(SyntheticCreationalContext<AgentCard> context) {
                return context.getInjectedReference(AgentCardProducer.class).produce(agent);
            }
        };
    }

    public RuntimeValue<DetectedTransports> detectedTransports(List<String> protocolBindings) {
        return new RuntimeValue<>(new DetectedTransports(protocolBindings));
    }
}
