package io.quarkiverse.langchain4j.a2a.server.runtime;

import java.util.List;
import java.util.function.Function;

import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.spec.AgentCard;
import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.a2a.server.runtime.authorization.DenyingTaskAuthorizationProvider;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.AgentCardProducer;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.DetectedTransports;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.ExposedAgent;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class A2AServerRecorder {

    private static final Logger LOG = Logger.getLogger(A2AServerRecorder.class);

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

    /**
     * Resolves the provider the way the SDK does, so that what is reported is what the SDK will do. A provider of the
     * application's own goes unreported.
     */
    public void reportTaskAuthorization() {
        InjectableInstance<TaskAuthorizationProvider> providers = Arc.container()
                .select(TaskAuthorizationProvider.class);
        if (!providers.isResolvable()) {
            LOG.warnf("A2A task authorization is off (%s=false) and the application supplies no"
                    + " TaskAuthorizationProvider, so any caller that reaches the endpoint can list and read"
                    + " every task, whichever tenant created it",
                    DenyingTaskAuthorizationProvider.AUTHORIZATION_REQUIRED);
        } else if (providers.getHandle().getBean().getBeanClass().equals(DenyingTaskAuthorizationProvider.class)) {
            LOG.warn(DenyingTaskAuthorizationProvider.EXPLANATION);
        }
    }
}
