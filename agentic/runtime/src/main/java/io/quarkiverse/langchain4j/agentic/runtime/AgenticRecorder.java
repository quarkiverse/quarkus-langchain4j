package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.agent.AgentInvocationHandler;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.agentic.runtime.devui.DevAgentMonitorHolder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InterceptionProxySubclass;
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
    private static Set<String> rootAgentClassNames = Collections.emptySet();

    @StaticInit
    public void setAgentsWithMcpToolBox(Set<String> agentsWithMcpToolBox) {
        AgenticRecorder.agentsWithMcpToolBox = Collections.unmodifiableSet(agentsWithMcpToolBox);
    }

    @StaticInit
    public void setLeafAgentClassNames(Set<String> leafAgentClassNames) {
        AgenticRecorder.leafAgentClassNames = Collections.unmodifiableSet(leafAgentClassNames);
    }

    @RuntimeInit
    public void registerChatSupplierParameterResolver() {
        DeclarativeUtil.addChatSupplierParameterResolver(new CdiChatSupplierParameterResolver());
    }

    @RuntimeInit
    public void enableDevModeMonitoring(Set<String> rootAgentClassNames) {
        DevAgentMonitorHolder.reset();
        AgenticRecorder.devModeMonitoringEnabled = true;
        AgenticRecorder.rootAgentClassNames = Collections.unmodifiableSet(rootAgentClassNames);
    }

    @RuntimeInit
    public void eagerlyInitRootAgents(Set<String> rootAgentClassNames) {
        for (String className : rootAgentClassNames) {
            try {
                // TCCL not reliable in dev-mode startup on virtual threads; use recorder classloader.
                Class<?> clazz = Class.forName(className, true, AgenticRecorder.class.getClassLoader());
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
                                QuarkusSubAgentResolver.INSTANCE));

                if (info.hasInterceptorBindings()) {
                    agent = cdiContext.getInterceptionProxy().create(agent);
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
                return unwrapProxies(ClientProxy.unwrap(resolved));
            }
            if (resolved instanceof InterceptionProxySubclass) {
                return new AgentInvocationHandler(resolved, InterceptionProxySubclass.unwrap(resolved)).get();
            }
            return resolved;
        }

        private static class AgentInvocationHandler implements InvocationHandler {

            private final Object wrapper;
            private final Object delegate;

            private final Set<Class<?>> wrapperInterfaces;
            private final Set<Class<?>> delegateInterfaces;

            AgentInvocationHandler(Object wrapper, Object delegate) {
                this.wrapper = wrapper;
                this.delegate = delegate;

                wrapperInterfaces = Set.of(wrapper.getClass().getInterfaces());
                delegateInterfaces = Set.of(delegate.getClass().getInterfaces());
            }

            Object get() {
                Set<Class<?>> allInterfaces = new HashSet<>(wrapperInterfaces);
                allInterfaces.addAll(delegateInterfaces);
                allInterfaces.remove(AgenticScopeOwner.class);
                Class<?>[] interfaces = allInterfaces.toArray(new Class<?>[0]);
                return Proxy.newProxyInstance(wrapper.getClass().getClassLoader(), interfaces, this);
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
                if (wrapperInterfaces.contains(method.getDeclaringClass())) {
                    return method.invoke(wrapper, args);
                }
                return method.invoke(delegate, args);
            }
        }
    }

    private static Class<?> loadClassSafe(AiAgentCreateInfo info) {
        try {
            // Do not use Thread.currentThread().getContextClassLoader() here — TCCL is not
            // guaranteed to be the deployment classloader on Vert.x I/O threads or virtual
            // threads spawned by Executors.newVirtualThreadPerTaskExecutor(). The recorder's
            // own classloader is always the deployment classloader.
            return Class.forName(info.agentClassName(), true, AgenticRecorder.class.getClassLoader());
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

    private static final class NoOpConsumer implements Consumer {

        private static final NoOpConsumer INSTANCE = new NoOpConsumer();

        @Override
        public void accept(Object t) {

        }
    }
}
