package io.quarkiverse.langchain4j.a2a.server.runtime;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.spec.AgentCard;
import org.eclipse.microprofile.config.ConfigProvider;
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
     * <p>
     * The SDK reads {@code a2a.authorization.required} with {@link Boolean#parseBoolean}, so any value but
     * {@code true} turns its check off, while {@link DenyingTaskAuthorizationProvider} only steps aside for
     * {@code false}. A value such as {@code FALSE} would leave every task operation denied by a check the
     * application believes is off, so startup fails instead.
     */
    public void reportTaskAuthorization() {
        Optional<String> required = ConfigProvider.getConfig()
                .getOptionalValue(DenyingTaskAuthorizationProvider.AUTHORIZATION_REQUIRED, String.class);
        boolean off = required.isPresent() && "false".equals(required.get());
        if (required.isPresent() && !off && !Boolean.parseBoolean(required.get())) {
            throw new IllegalStateException("'" + DenyingTaskAuthorizationProvider.AUTHORIZATION_REQUIRED
                    + "' is set to '" + required.get() + "', which the A2A Java SDK reads as false while every A2A"
                    + " task operation stays denied. Set it to 'false' to turn task authorization off, or to 'true'"
                    + " to keep it on");
        }
        InjectableInstance<TaskAuthorizationProvider> providers = Arc.container()
                .select(TaskAuthorizationProvider.class);
        if (!providers.isResolvable()) {
            if (off) {
                LOG.warnf("A2A task authorization is off (%s=false) and the application supplies no"
                        + " TaskAuthorizationProvider, so any caller that reaches the endpoint can list and read"
                        + " every task, whichever tenant created it",
                        DenyingTaskAuthorizationProvider.AUTHORIZATION_REQUIRED);
            } else {
                LOG.warn("A2A task authorization is on but no TaskAuthorizationProvider can be resolved, so every"
                        + " A2A task operation is denied and reported to the client as TaskNotFoundError");
            }
        } else if (providers.getHandle().getBean().getBeanClass().equals(DenyingTaskAuthorizationProvider.class)) {
            LOG.warn(DenyingTaskAuthorizationProvider.EXPLANATION);
        }
    }
}
