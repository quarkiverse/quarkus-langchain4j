package io.quarkiverse.langchain4j.agentic.runtime;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.agentic.runtime.devui.DevAgentMonitorHolder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.annotations.RuntimeInit;
import io.quarkus.runtime.annotations.StaticInit;

@Recorder
public class AgenticRecorder {

    private static final Logger log = Logger.getLogger(AgenticRecorder.class);
    private static volatile Set<String> agentsWithMcpToolBox = Collections.emptySet();
    private static volatile Set<String> leafAgentClassNames = Collections.emptySet();
    private static volatile boolean devModeMonitoringEnabled = false;
    private static volatile Map<String, AgentClassCreateInfo> agentClassMetadata = Map.of();

    private static final Function<InternalAgent, Object> AGENT_INSTANCE_FACTORY = internalAgent -> {
        AgentClassCreateInfo info = agentClassMetadata.get(internalAgent.type().getName());
        if (info == null) {
            throw new RuntimeException("No generated agent class for: " + internalAgent.type().getName());
        }
        try {
            return Class.forName(info.implClassName(), true,
                    Thread.currentThread().getContextClassLoader())
                    .getConstructor(InternalAgent.class)
                    .newInstance(internalAgent);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create agent class '" + info.implClassName() + "'", e);
        }
    };

    @StaticInit
    public void setAgentsWithMcpToolBox(Set<String> agentsWithMcpToolBox) {
        AgenticRecorder.agentsWithMcpToolBox = Collections.unmodifiableSet(agentsWithMcpToolBox);
    }

    @StaticInit
    public void setLeafAgentClassNames(Set<String> leafAgentClassNames) {
        AgenticRecorder.leafAgentClassNames = Collections.unmodifiableSet(leafAgentClassNames);
    }

    @StaticInit
    public void setAgentClassMetadata(Map<String, AgentClassCreateInfo> metadata) {
        AgenticRecorder.agentClassMetadata = Map.copyOf(metadata);
    }

    @RuntimeInit
    public void registerChatSupplierParameterResolver() {
        DeclarativeUtil.addChatSupplierParameterResolver(new CdiChatSupplierParameterResolver());
    }

    @RuntimeInit
    public void enableDevModeMonitoring(Set<String> rootAgentClassNames) {
        DevAgentMonitorHolder.reset();
        AgenticRecorder.devModeMonitoringEnabled = true;
        for (String className : rootAgentClassNames) {
            try {
                Class<?> clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
                ClientProxy.unwrap(Arc.container().select(clazz).get());
            } catch (Exception e) {
                log.warn("Failed to eagerly initialize root agent for dev mode topology: " + className, e);
            }
        }
    }

    @RuntimeInit
    public Function<SyntheticCreationalContext<Object>, Object> createAiAgent(AiAgentCreateInfo info) {
        return new Function<>() {
            @Override
            public Object apply(SyntheticCreationalContext<Object> cdiContext) {
                ChatModel chatModel;
                if (info.chatModelInfo() instanceof AiAgentCreateInfo.ChatModelInfo.FromAnnotation) {
                    chatModel = null;
                } else if (info.chatModelInfo() instanceof AiAgentCreateInfo.ChatModelInfo.FromBeanWithName b) {
                    if (NamedConfigUtil.isDefault(b.name())) {
                        chatModel = cdiContext.getInjectedReference(ChatModel.class);
                    } else {
                        chatModel = cdiContext.getInjectedReference(ChatModel.class, ModelName.Literal.of(b.name()));
                    }
                } else {
                    throw new IllegalStateException("Unknown type: " + info.chatModelInfo().getClass());
                }

                Class<?> agentClass = loadClassSafe(info);
                Object agent = AgenticServices.createAgenticSystem(agentClass, chatModel,
                        new AgentConfigurator(new QuarkusAgenticContextConsumer(cdiContext, info),
                                QuarkusSubAgentResolver.INSTANCE, AGENT_INSTANCE_FACTORY));

                if (info.hasInterceptorBindings()) {
                    Object originalAgent = agent;
                    agent = cdiContext.getInterceptionProxy().create(originalAgent);
                    try {
                        originalAgent.getClass().getMethod("setAgentProxy", Object.class)
                                .invoke(originalAgent, agent);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to set agent proxy on " + originalAgent.getClass(), e);
                    }
                }

                if (devModeMonitoringEnabled && agent instanceof MonitoredAgent monitoredAgent) {
                    AgentMonitor monitor = monitoredAgent.agentMonitor();
                    if (monitor != null) {
                        DevAgentMonitorHolder.register(monitor);
                        DevAgentMonitorHolder.registerRootAgent(agent);
                    }
                }
                return agent;
            }
        };
    }

    private static final class QuarkusSubAgentResolver implements Function<Class<?>, Object> {

        private static final QuarkusSubAgentResolver INSTANCE = new QuarkusSubAgentResolver();

        @Override
        public Object apply(Class<?> subAgentClass) {
            if (subAgentClass == null) {
                return null;
            }

            if (!leafAgentClassNames.contains(subAgentClass.getName())) {
                return null;
            }

            try {
                return unwrapProxies(Arc.container().select(subAgentClass).orNull());
            } catch (Exception e) {
                log.debugf(e,
                        "Unable to resolve CDI sub-agent '%s'; falling back to LangChain4j internal creation",
                        subAgentClass.getName());
                return null;
            }
        }

        private static Object unwrapProxies(Object resolved) {
            if (resolved instanceof ClientProxy) {
                resolved = ClientProxy.unwrap(resolved);
            }
            return resolved;
        }
    }

    private static Class<?> loadClassSafe(AiAgentCreateInfo info) {
        try {
            return Class.forName(info.agentClassName(), true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            log.error("Unable to load agent class '" + info.agentClassName() + "'", e);
            throw new RuntimeException(e);
        }
    }

    private record QuarkusAgenticContextConsumer(SyntheticCreationalContext<Object> cdiContext,
            AiAgentCreateInfo aiAgentCreateInfo)
            implements
                Consumer<AgenticServices.DeclarativeAgentCreationContext<?>> {

        private static final TypeLiteral<Instance<ToolProvider>> TOOL_PROVIDER_TYPE_LITERAL = new TypeLiteral<>() {
        };

        @Override
        public void accept(AgenticServices.DeclarativeAgentCreationContext agenticContext) {
            if (AgenticRecorder.agentsWithMcpToolBox.contains(agenticContext.agentServiceClass().getName())) {
                Instance<ToolProvider> injectedReference = cdiContext.getInjectedReference(TOOL_PROVIDER_TYPE_LITERAL);
                if (injectedReference.isResolvable()) {
                    agenticContext.agentBuilder().toolProvider(injectedReference.get());
                }
            }
        }
    }
}
