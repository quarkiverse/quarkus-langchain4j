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

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.agent.AgentInvocationHandler;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.agentic.runtime.devui.DevAgentMonitorHolder;
import io.quarkiverse.langchain4j.agentic.runtime.observability.AgentHealthCheck;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InterceptionProxySubclass;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.annotations.RuntimeInit;
import io.quarkus.runtime.annotations.StaticInit;

@Recorder
public class AgenticRecorder {

    private static final Logger log = Logger.getLogger(AgenticRecorder.class);
    private static volatile Set<String> leafAgentClassNames = Collections.emptySet();
    private static volatile boolean devModeMonitoringEnabled = false;
    private static Set<String> rootAgentClassNames = Collections.emptySet();

    final RuntimeValue<AgenticRuntimeConfig> runtimeConfig;

    public AgenticRecorder(RuntimeValue<AgenticRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
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
    public void registerDefaultExecutorProvider() {
        ManagedExecutor managedExecutor = Arc.container().instance(ManagedExecutor.class).get();
        if (managedExecutor == null) {
            log.warn("ManagedExecutor not available — parallel agents will use raw virtual threads "
                    + "without CDI/OTel/Security context propagation");
            return;
        }
        try {
            java.lang.reflect.Method setter = DefaultExecutorProvider.class
                    .getMethod("setDefaultExecutorService", java.util.concurrent.ExecutorService.class);
            setter.invoke(null, managedExecutor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register ManagedExecutor as default executor provider", e);
        }
    }

    @RuntimeInit
    public void enableDevModeMonitoring(Set<String> rootAgentClassNames, ShutdownContext shutdownContext) {
        DevAgentMonitorHolder.reset();
        AgenticRecorder.devModeMonitoringEnabled = true;
        AgenticRecorder.rootAgentClassNames = Collections.unmodifiableSet(rootAgentClassNames);
        shutdownContext.addShutdownTask(DevAgentMonitorHolder::reset);
    }

    @RuntimeInit
    public void setHealthCheckAgentClassNames(Set<String> agentClassNames) {
        AgentHealthCheck.setRootAgentClassNames(agentClassNames);
    }

    @RuntimeInit
    public void conditionallyEagerInitRootAgents(Set<String> rootAgentClassNames) {
        if (runtimeConfig.getValue().devUi().eagerInit()) {
            eagerlyInitRootAgents(rootAgentClassNames);
        }
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

                Class<?> agentClass = loadClassSafe(info, cdiContext.getClass().getClassLoader());
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

    private static Class<?> loadClassSafe(AiAgentCreateInfo info, ClassLoader contextCl) {
        // Classloader resolution strategy (tried in order):
        // 1. contextCl — the classloader of the SyntheticCreationalContext generated class.
        //    This is the Quarkus augmentation CL and knows about ALL application and test classes
        //    including @QuarkusTest inner classes.  This is the most reliable source.
        // 2. TCCL — may work on normal threads but is unreliable on Vert.x I/O threads and
        //    virtual threads spawned by Executors.newVirtualThreadPerTaskExecutor().
        // 3. Recorder's own classloader — runtime module CL; correct for production/dev mode
        //    worker threads where TCCL may be the system CL.
        try {
            return Class.forName(info.agentClassName(), true, contextCl);
        } catch (ClassNotFoundException ignored) {
            // fall through
        }
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != contextCl) {
            try {
                return Class.forName(info.agentClassName(), true, tccl);
            } catch (ClassNotFoundException ignored) {
                // fall through
            }
        }
        ClassLoader recorderCl = AgenticRecorder.class.getClassLoader();
        if (recorderCl != contextCl && recorderCl != tccl) {
            try {
                return Class.forName(info.agentClassName(), true, recorderCl);
            } catch (ClassNotFoundException ignored) {
                // fall through
            }
        }
        log.error("Unable to load agent class '" + info.agentClassName()
                + "' from any classloader (context, TCCL, or recorder)");
        throw new RuntimeException(new ClassNotFoundException(info.agentClassName()));
    }

    private record QuarkusAgenticContextConsumer(SyntheticCreationalContext<Object> cdiContext,
            AiAgentCreateInfo aiAgentCreateInfo)
            implements
                Consumer<AgenticServices.DeclarativeAgentCreationContext<?>> {

        private static final TypeLiteral<Instance<ToolProvider>> TOOL_PROVIDER_INSTANCE = new TypeLiteral<>() {
        };
        private static final TypeLiteral<Instance<AgentListener>> AGENT_LISTENER_INSTANCE = new TypeLiteral<>() {
        };

        @Override
        public void accept(AgenticServices.DeclarativeAgentCreationContext agenticContext) {
            var agentBuilder = agenticContext.agentBuilder();

            // MCP ToolProvider support (uses build-time flag from AiAgentCreateInfo)
            if (aiAgentCreateInfo.hasMcpToolBox()) {
                Instance<ToolProvider> toolProviderInstance = cdiContext.getInjectedReference(TOOL_PROVIDER_INSTANCE);
                if (toolProviderInstance.isResolvable()) {
                    agentBuilder.toolProvider(toolProviderInstance.get());
                }
            }

            // AgentListener support (unconditional — build-time always adds the injection point)
            Instance<AgentListener> listeners = cdiContext.getInjectedReference(AGENT_LISTENER_INSTANCE);
            for (AgentListener listener : listeners) {
                agentBuilder.listener(listener);
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
