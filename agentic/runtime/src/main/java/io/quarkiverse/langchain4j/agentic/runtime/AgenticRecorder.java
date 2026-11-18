package io.quarkiverse.langchain4j.agentic.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

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
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.decision.ThresholdProvider;
import dev.langchain4j.service.tool.ToolProvider;
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
    private static volatile Set<String> agentsWithAgentScopedMemory = Collections.emptySet();
    private static volatile Set<String> leafAgentClassNames = Collections.emptySet();
    private static volatile boolean devModeMonitoringEnabled = false;
    private static volatile Map<String, AgentClassCreateInfo> agentClassMetadata = Map.of();
    private static volatile Map<String, DecisionAgentInfo> decisionAgents = Map.of();

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
    public void setAgentsWithAgentScopedMemory(Set<String> agentsWithAgentScopedMemory) {
        AgenticRecorder.agentsWithAgentScopedMemory = Collections.unmodifiableSet(agentsWithAgentScopedMemory);
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
                ClientProxy.unwrap(Arc.container().select(clazz).get());
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
                Object agent;
                if (info.isDecisionAgent()) {
                    agent = createDecisionAgent(agentClass, info, cdiContext);
                } else {
                    ChatModel chatModel = info.chatModelInfo().resolve(cdiContext);
                    agent = AgenticServices.createAgenticSystem(agentClass, chatModel,
                            new AgentConfigurator(new QuarkusAgenticContextConsumer(cdiContext, info, null,
                                    runtimeConfig.getValue()), QuarkusSubAgentResolver.INSTANCE, AGENT_INSTANCE_FACTORY));
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
    private Object createDecisionAgent(Class<?> agentClass, AiAgentCreateInfo info,
            SyntheticCreationalContext<Object> cdiContext) {
        DecisionModel decisionModel = info.decisionModelInfo().resolve(cdiContext);
        DecisionAgentBuilder<?> builder = DecisionAgentBuilder.withoutDeclarativeConfiguration(agentClass);
        DeclarativeUtil.configureDecisionAgent(agentClass, builder,
                new AgentConfigurator(
                        new QuarkusAgenticContextConsumer(cdiContext, info, decisionModel, runtimeConfig.getValue()),
                        QuarkusSubAgentResolver.INSTANCE, AGENT_INSTANCE_FACTORY));
        return builder.build();
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

    /**
     * @param decisionModel the decision model of the decision agent whose bean is being created, {@code null} when
     *        it is provided by its {@code @DecisionModelSupplier} method or when the bean is not a decision agent
     */
    private record QuarkusAgenticContextConsumer(SyntheticCreationalContext<Object> cdiContext,
            AiAgentCreateInfo aiAgentCreateInfo, DecisionModel decisionModel, AgenticRuntimeConfig runtimeConfig)
            implements
                Consumer<AgenticServices.DeclarativeAgentCreationContext<?>> {

        private static final TypeLiteral<Instance<ToolProvider>> TOOL_PROVIDER_INSTANCE = new TypeLiteral<>() {
        };
        private static final TypeLiteral<Instance<AgentListener>> AGENT_LISTENER_INSTANCE = new TypeLiteral<>() {
        };

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
                Instance<ToolProvider> toolProviderInstance = cdiContext.getInjectedReference(TOOL_PROVIDER_INSTANCE);
                if (toolProviderInstance.isResolvable()) {
                    agentBuilder.toolProvider(toolProviderInstance.get());
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

            // @MemoryId support: each agent keeps its own conversation in the default chat memory store
            if (AgenticRecorder.agentsWithAgentScopedMemory.contains(agentClassName)) {
                ChatMemoryProvider defaultProvider = Arc.container().select(ChatMemoryProvider.class).get();
                agentBuilder.chatMemoryProvider(new AgentScopedChatMemoryProvider(defaultProvider, agentClassName));
            }

            // AgentListener support (unconditional — build-time always adds the injection point)
            Instance<AgentListener> listeners = cdiContext.getInjectedReference(AGENT_LISTENER_INSTANCE);
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

            Instance<AgentListener> listeners = cdiContext.getInjectedReference(AGENT_LISTENER_INSTANCE);
            for (AgentListener listener : listeners) {
                builder.listener(listener);
            }
        }
    }

    /**
     * Keys the conversations of an agent in the default chat memory store by memory id and agent, so agents invoked
     * with the same memory id keep separate conversations.
     */
    private record AgentScopedChatMemoryProvider(ChatMemoryProvider delegate, String agentClassName)
            implements
                ChatMemoryProvider {

        @Override
        public ChatMemory get(Object memoryId) {
            return delegate.get(memoryId + "#" + agentClassName);
        }
    }
}
