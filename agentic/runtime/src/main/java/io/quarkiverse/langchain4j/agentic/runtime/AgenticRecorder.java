package io.quarkiverse.langchain4j.agentic.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.agent.DecisionAgentBuilder;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.decision.ThresholdProvider;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.spi.ExecutorProvider;
import io.quarkiverse.langchain4j.agentic.runtime.devui.DevAgentMonitorHolder;
import io.quarkiverse.langchain4j.runtime.decision.ConfiguredThresholds;
import io.quarkiverse.langchain4j.runtime.skills.SkillsConfigurator;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.annotations.RuntimeInit;
import io.quarkus.runtime.annotations.StaticInit;
import io.quarkus.runtime.configuration.ConfigurationException;

@Recorder
public class AgenticRecorder {

    private static final Logger log = Logger.getLogger(AgenticRecorder.class);
    private static volatile Set<String> agentsWithMcpToolBox = Collections.emptySet();
    private static volatile Map<String, List<String>> agentsWithToolBox = Map.of();
    private static volatile Map<String, List<String>> agentsWithSkills = Map.of();
    private static volatile Set<String> leafAgentClassNames = Collections.emptySet();
    private static volatile boolean devModeMonitoringEnabled = false;
    private static volatile Map<String, AgentClassCreateInfo> agentClassMetadata = Map.of();
    private static volatile Map<String, DecisionAgentInfo> decisionAgents = Map.of();
    private static volatile Map<String, AiAgentCreateInfo> classBasedAgents = Map.of();
    private static volatile Set<String> instanceAgentBeanClassNames = Collections.emptySet();
    private static volatile AgenticRuntimeConfig classBasedAgentsRuntimeConfig;

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

    /**
     * The helper objects of an agent type: the bean of a class-based agent, or of a bean with an instance agent
     * method, whose instance helper methods are invoked through its client proxy.
     */
    private static final Function<Class<?>, List<?>> HELPER_OBJECTS_RESOLVER = agentType -> {
        String name = agentType.getName();
        if (classBasedAgents.containsKey(name) || instanceAgentBeanClassNames.contains(name)) {
            return List.of(Arc.container().instance(agentType).get());
        }
        return List.of();
    };

    /**
     * The configurator of an agent of the extension: the given consumer tweaks each agent of the system, and the
     * other hooks bring the beans, the generated agent classes and the memory id of a scoped instance.
     */
    private static AgentConfigurator agentConfigurator(QuarkusAgenticContextConsumer contextConsumer,
            Supplier<Object> defaultMemoryIdSupplier) {
        return AgentConfigurator.builder()
                .configurator(contextConsumer)
                .subAgentResolver(QuarkusSubAgentResolver.INSTANCE)
                .agentInstanceFactory(AGENT_INSTANCE_FACTORY)
                .defaultMemoryIdSupplier(defaultMemoryIdSupplier)
                .helperObjectsResolver(HELPER_OBJECTS_RESOLVER)
                .build();
    }

    private final RuntimeValue<AgenticRuntimeConfig> runtimeConfig;

    public AgenticRecorder(RuntimeValue<AgenticRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    @StaticInit
    public void setAgentsWithMcpToolBox(Set<String> agentsWithMcpToolBox) {
        AgenticRecorder.agentsWithMcpToolBox = Collections.unmodifiableSet(agentsWithMcpToolBox);
    }

    @StaticInit
    public void setAgentsWithToolBox(Map<String, List<String>> agentsWithToolBox) {
        AgenticRecorder.agentsWithToolBox = Map.copyOf(agentsWithToolBox);
    }

    @StaticInit
    public void setAgentsWithSkills(Map<String, List<String>> agentsWithSkills) {
        AgenticRecorder.agentsWithSkills = Map.copyOf(agentsWithSkills);
    }

    @StaticInit
    public void setLeafAgentClassNames(Set<String> leafAgentClassNames) {
        AgenticRecorder.leafAgentClassNames = Collections.unmodifiableSet(leafAgentClassNames);
    }

    @StaticInit
    public void setAgentClassMetadata(Map<String, AgentClassCreateInfo> metadata) {
        AgenticRecorder.agentClassMetadata = Map.copyOf(metadata);
    }

    /**
     * @param decisionAgents the decision agents, keyed by the name of their interface
     */
    @StaticInit
    public void setDecisionAgents(Map<String, DecisionAgentInfo> decisionAgents) {
        AgenticRecorder.decisionAgents = Map.copyOf(decisionAgents);
    }

    /**
     * Fails the startup when a threshold of a decision agent is configured with an invalid value or for an agent on
     * which it would have no effect, and warns about the keys that match no decision agent.
     */
    @RuntimeInit
    public void validateDecisionAgentConfig() {
        Map<String, List<DecisionAgentInfo>> agentsByName = new HashMap<>();
        for (DecisionAgentInfo agent : decisionAgents.values()) {
            List<DecisionAgentInfo> agents = agentsByName.get(agent.agentName());
            if (agents == null) {
                agents = new ArrayList<>();
                agentsByName.put(agent.agentName(), agents);
            }
            agents.add(agent);
        }
        for (Map.Entry<String, AgenticRuntimeConfig.DecisionAgentConfig> entry : runtimeConfig.getValue().decisionAgents()
                .entrySet()) {
            String agentName = entry.getKey();
            AgenticRuntimeConfig.DecisionAgentConfig config = entry.getValue();
            String key = "quarkus.langchain4j.agentic.decision-agent.\"" + agentName + "\"";
            ConfiguredThresholds.validate(key, config.threshold(), config.thresholdByModel());
            List<DecisionAgentInfo> agents = agentsByName.get(agentName);
            if (agents == null) {
                log.warnf("The configuration '%s' has no effect: there is no decision agent named '%s'", key, agentName);
                continue;
            }
            for (DecisionAgentInfo agent : agents) {
                if (!agent.booleanAnswer()) {
                    throw new ConfigurationException(("The configuration '%s' has no effect: the decision agent '%s' has "
                            + "no boolean answer, so a threshold would never be used. Apply it to the probabilities of its "
                            + "YesNoAnswer, Choice or Scale answer instead").formatted(key, agentName));
                }
                if (agent.hasThresholdSupplier()) {
                    throw new ConfigurationException(("The configuration '%s' has no effect: the decision agent '%s' "
                            + "declares a @DecisionThresholdSupplier method, which takes precedence").formatted(key,
                                    agentName));
                }
            }
        }
    }

    @RuntimeInit
    public void registerAgenticScopeDeserializationTypes(Set<String> classNames) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        AgenticScopeSerializer.withClassLoader(cl);
        for (String className : classNames) {
            try {
                AgenticScopeSerializer.allowDeserializationType(Class.forName(className, true, cl));
            } catch (ClassNotFoundException e) {
                log.warnf("Unable to register class '%s' for AgenticScope deserialization", className);
            }
        }
    }

    @RuntimeInit
    public void registerSupplierParameterResolver(Set<String> qualifierNames) {
        // langchain4j-agentic is not reloaded in dev mode: replace the resolver registered before the restart
        List<SupplierParameterResolver> resolvers = DeclarativeUtil.getSupplierParameterResolvers();
        for (SupplierParameterResolver resolver : resolvers) {
            if (resolver instanceof CdiSupplierParameterResolver) {
                resolvers.remove(resolver);
            }
        }
        DeclarativeUtil.addSupplierParameterResolver(
                new CdiSupplierParameterResolver(Collections.unmodifiableSet(qualifierNames)));
    }

    /**
     * Initializes the class-based agents, the beans whose agentic method is {@code native}, and the beans with an
     * instance agent method; called at every start, so that the agents created before a restart in dev mode are
     * forgotten.
     *
     * @param agents the class-based agents, keyed by class name
     * @param instanceAgentBeanClassNames the names of the beans with an instance {@code @Agent} or
     *        {@code @HumanInTheLoop} method
     * @param perInstanceScopeClassNames the names of the class-based agents whose scope is not the application scope
     */
    @RuntimeInit
    public void initClassBasedAgents(Map<String, AiAgentCreateInfo> agents, Set<String> instanceAgentBeanClassNames,
            Set<String> perInstanceScopeClassNames) {
        AgenticRecorder.classBasedAgents = Map.copyOf(agents);
        AgenticRecorder.instanceAgentBeanClassNames = Set.copyOf(instanceAgentBeanClassNames);
        AgenticRecorder.classBasedAgentsRuntimeConfig = runtimeConfig.getValue();
        AgentClassSupport.init(AgenticRecorder::createClassBasedAgent, agents.keySet(), perInstanceScopeClassNames);
    }

    /**
     * Makes langchain4j-agentic run the parallel and asynchronous agents on the executor of the core extension, which
     * propagates the request context.
     */
    @RuntimeInit
    public void registerExecutorProvider() {
        ExecutorProvider.set(new QuarkusExecutorProvider());
    }

    @RuntimeInit
    public void setDevUIAllowedAgentClassNames(Set<String> classNames) {
        DevAgentMonitorHolder.allowedAgentClassNames = Collections.unmodifiableSet(classNames);
    }

    @RuntimeInit
    public void enableDevModeMonitoring(Set<String> rootAgentClassNames) {
        DevAgentMonitorHolder.reset();
        AgenticRecorder.devModeMonitoringEnabled = true;
        for (String className : rootAgentClassNames) {
            try {
                Class<?> clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
                if (classBasedAgents.containsKey(className)) {
                    AgentClassSupport.rootAgent(clazz);
                } else {
                    ClientProxy.unwrap(Arc.container().select(clazz).get());
                }
            } catch (Exception e) {
                log.warn("Failed to eagerly initialize root agent for dev mode topology: " + className, e);
            }
        }
    }

    @RuntimeInit
    public void validateSkillNames(BeanContainer beanContainer, Set<String> skillNames) {
        SkillsConfigurator configurator = beanContainer.beanInstance(SkillsConfigurator.class);
        if (configurator == null) {
            throw new IllegalStateException(
                    "@Skills annotation requires the quarkus-langchain4j-skills extension");
        }
        configurator.createToolProvider(new ArrayList<>(skillNames));
    }

    @RuntimeInit
    public Function<SyntheticCreationalContext<Object>, Object> createAiAgent(AiAgentCreateInfo info) {
        return new Function<>() {
            @Override
            public Object apply(SyntheticCreationalContext<Object> cdiContext) {
                Class<?> agentClass = loadClassSafe(info);
                // an instance of an agent with a scope other than the application scope has its own memory id
                String instanceMemoryId = info.perInstanceScope() ? UUID.randomUUID().toString() : null;
                Supplier<Object> defaultMemoryIdSupplier = instanceMemoryId == null ? null : () -> instanceMemoryId;
                QuarkusAgenticContextConsumer contextConsumer = new QuarkusAgenticContextConsumer(
                        cdiContext.getInjectedReference(TOOL_PROVIDER_INSTANCE),
                        cdiContext.getInjectedReference(AGENT_LISTENER_INSTANCE), info, null, runtimeConfig.getValue());
                Object agent;
                if (info.isDecisionAgent()) {
                    agent = createDecisionAgent(agentClass, info, info.decisionModelInfo().resolve(cdiContext),
                            cdiContext.getInjectedReference(TOOL_PROVIDER_INSTANCE),
                            cdiContext.getInjectedReference(AGENT_LISTENER_INSTANCE), defaultMemoryIdSupplier);
                } else {
                    ChatModel chatModel = info.chatModelInfo().resolve(cdiContext);
                    agent = AgenticServices.createAgenticSystem(agentClass, chatModel,
                            agentConfigurator(contextConsumer, defaultMemoryIdSupplier));
                }
                if (instanceMemoryId != null && agent instanceof AbstractQuarkusAgent quarkusAgent) {
                    quarkusAgent.setInstanceMemoryId(instanceMemoryId);
                }

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

    /**
     * Builds a decision agent directly, rather than with {@link AgenticServices#createAgenticSystem}, which only
     * recognizes the decision agents whose {@code @Agent} method is annotated with {@code @Decide} or that declare a
     * {@code @DecisionModelSupplier} method, and not those returning an object with {@code @Decide} fields.
     */
    private static Object createDecisionAgent(Class<?> agentClass, AiAgentCreateInfo info, DecisionModel decisionModel,
            Instance<ToolProvider> toolProviders, Instance<AgentListener> listeners,
            Supplier<Object> defaultMemoryIdSupplier) {
        DecisionAgentBuilder<?> builder = DecisionAgentBuilder.withoutDeclarativeConfiguration(agentClass);
        DeclarativeUtil.configureDecisionAgent(agentClass, builder,
                agentConfigurator(
                        new QuarkusAgenticContextConsumer(toolProviders, listeners, info, decisionModel,
                                classBasedAgentsRuntimeConfig),
                        defaultMemoryIdSupplier));
        return builder.build();
    }

    /**
     * Creates the LangChain4j agent object of a class-based agent, an instance of its generated subclass, on its
     * first use: the bean of the class is its helper object, and its sub-agents are resolved like the ones of an
     * agent interface. The chat model and the decision model are looked up in the container.
     */
    private static Object createClassBasedAgent(Class<?> agentClass) {
        AiAgentCreateInfo info = classBasedAgents.get(agentClass.getName());
        if (info == null) {
            throw new IllegalStateException("The class " + agentClass.getName() + " is not a class-based agent");
        }
        Instance<ToolProvider> toolProviders = Arc.container().select(ToolProvider.class);
        Instance<AgentListener> listeners = Arc.container().select(AgentListener.class);
        Supplier<Object> defaultMemoryIdSupplier = info.perInstanceScope() ? AgentClassSupport::currentInstanceMemoryId
                : null;
        Object agent;
        if (info.isDecisionAgent()) {
            agent = createDecisionAgent(agentClass, info, info.decisionModelInfo().resolve(), toolProviders, listeners,
                    defaultMemoryIdSupplier);
        } else {
            ChatModel chatModel = info.chatModelInfo().resolve();
            agent = AgenticServices.createAgenticSystem(agentClass, chatModel,
                    agentConfigurator(
                            new QuarkusAgenticContextConsumer(toolProviders, listeners, info, null,
                                    classBasedAgentsRuntimeConfig),
                            defaultMemoryIdSupplier));
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

    private static final class QuarkusSubAgentResolver implements Function<Class<?>, Object> {

        private static final QuarkusSubAgentResolver INSTANCE = new QuarkusSubAgentResolver();

        @Override
        public Object apply(Class<?> subAgentClass) {
            if (subAgentClass == null) {
                return null;
            }

            // the LangChain4j agent object of a class-based leaf agent, shared with the bean of the class; a
            // class-based workflow is built by langchain4j-agentic for each parent, like an interface workflow
            if (classBasedAgents.containsKey(subAgentClass.getName())) {
                return leafAgentClassNames.contains(subAgentClass.getName()) ? AgentClassSupport.rootAgent(subAgentClass)
                        : null;
            }

            // a bean with an instance agent method is invoked through its client proxy, so that its interceptors
            // apply, and is its own helper object
            if (instanceAgentBeanClassNames.contains(subAgentClass.getName())) {
                return Arc.container().select(subAgentClass).get();
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

    private static final TypeLiteral<Instance<ToolProvider>> TOOL_PROVIDER_INSTANCE = new TypeLiteral<>() {
    };
    private static final TypeLiteral<Instance<AgentListener>> AGENT_LISTENER_INSTANCE = new TypeLiteral<>() {
    };

    /**
     * @param toolProviders the {@code ToolProvider} beans, for the agents with an MCP tool box
     * @param listeners the {@code AgentListener} beans, registered on every agent
     * @param decisionModel the decision model of the decision agent whose bean is being created, {@code null} when
     *        it is provided by its {@code @DecisionModelSupplier} method or when the bean is not a decision agent
     */
    private record QuarkusAgenticContextConsumer(Instance<ToolProvider> toolProviders, Instance<AgentListener> listeners,
            AiAgentCreateInfo aiAgentCreateInfo, DecisionModel decisionModel, AgenticRuntimeConfig runtimeConfig)
            implements
                Consumer<AgenticServices.DeclarativeAgentCreationContext<?>> {

        @Override
        public void accept(AgenticServices.DeclarativeAgentCreationContext agenticContext) {
            if (agenticContext.agentBuilder() == null) {
                configureDecisionAgent(agenticContext.decisionAgentBuilder(), agenticContext.agentServiceClass());
                return;
            }
            var agentBuilder = agenticContext.agentBuilder();
            String agentClassName = agenticContext.agentServiceClass().getName();

            // MCP ToolProvider support
            if (AgenticRecorder.agentsWithMcpToolBox.contains(agentClassName)) {
                if (toolProviders.isResolvable()) {
                    agentBuilder.toolProvider(toolProviders.get());
                }
            }

            // @ToolBox support — resolve tool beans by class name
            List<String> toolClassNames = AgenticRecorder.agentsWithToolBox.get(agentClassName);
            if (toolClassNames != null) {
                List<Object> tools = new ArrayList<>(toolClassNames.size());
                for (String toolClassName : toolClassNames) {
                    try {
                        Class<?> toolClass = Class.forName(toolClassName, true,
                                Thread.currentThread().getContextClassLoader());
                        tools.add(Arc.container().select(toolClass).get());
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Unable to load @ToolBox class: " + toolClassName, e);
                    }
                }
                agentBuilder.tools(tools.toArray());
            }

            // @Skills support — configure skills tool provider and system message
            List<String> skillNames = AgenticRecorder.agentsWithSkills.get(agentClassName);
            if (skillNames != null) {
                SkillsConfigurator configurator = Arc.container().select(SkillsConfigurator.class).get();
                agentBuilder.toolProvider(configurator.createToolProvider(skillNames));
                String skillsMsg = configurator.buildSkillsSystemMessage(skillNames);
                BiFunction<String, InvocationContext, String> transformer = (
                        msg, ctx) -> (msg != null && !msg.isBlank()) ? msg + "\n\n" + skillsMsg : skillsMsg;
                agentBuilder.systemMessageTransformer(transformer);
            }

            // AgentListener support (unconditional — build-time always adds the injection point)
            for (AgentListener listener : listeners) {
                agentBuilder.listener(listener);
            }
        }

        /**
         * Applies the decision model bean, the configured thresholds and the {@code AgentListener} beans to a decision
         * agent. Its tools, MCP tool boxes and skills are rejected at build time.
         */
        private void configureDecisionAgent(DecisionAgentBuilder<?> builder, Class<?> agentClass) {
            // a @DecisionModelSupplier method has already set the model or its provider, and setting both fails
            if (decisionModel != null && agentClass.getName().equals(aiAgentCreateInfo.agentClassName())
                    && !builder.hasDecisionModel()) {
                builder.decisionModel(decisionModel);
            }

            DecisionAgentInfo info = decisionAgents.get(agentClass.getName());
            AgenticRuntimeConfig.DecisionAgentConfig config = info == null ? null
                    : runtimeConfig.decisionAgents().get(info.agentName());
            if (config != null && info.booleanAnswer() && !info.hasThresholdSupplier()) {
                ThresholdProvider thresholdProvider = ConfiguredThresholds.provider(config.threshold(),
                        config.thresholdByModel());
                if (thresholdProvider != null) {
                    builder.thresholdProvider(thresholdProvider);
                }
            }

            for (AgentListener listener : listeners) {
                builder.listener(listener);
            }
        }
    }
}
