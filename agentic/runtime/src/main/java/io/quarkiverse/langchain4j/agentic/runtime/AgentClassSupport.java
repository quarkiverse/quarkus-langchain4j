package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import dev.langchain4j.agentic.agent.AgentInvocationHandler;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryRemovable;
import io.quarkus.arc.Arc;

/**
 * The runtime of the class-based agents: the agents that are CDI beans, whose agentic method is {@code native} and
 * implemented at build time with a call to {@link #invoke}. For each class there is one LangChain4j agent object,
 * an instance of a generated subclass of the class created lazily by {@link #rootAgent}, that every bean instance of
 * the class delegates to; the helper methods of the agent are invoked by langchain4j-agentic on the bean itself,
 * through its client proxy.
 * <p>
 * When a workflow invokes a class-based agent as its sub-agent, the generated subclass routes the call through the
 * bean, so that the CDI interceptors bound to the agent method apply, and the bean delegates back to the agent
 * object that routed the call: the object is kept in a thread-local while the bean is invoked.
 * <p>
 * A bean instance of a class with a scope other than the application scope has its own memory id, created on its
 * first invocation and evicted with the agentic scope and the chat memories created for it when the instance is
 * destroyed.
 */
public final class AgentClassSupport {

    /** The marker of a call of the agent object by its bean, as opposed to a call by a workflow. */
    private static final Object ROOT = new Object();

    private static final ThreadLocal<Object> DELEGATE = new ThreadLocal<>();
    private static final ThreadLocal<Object> MEMORY_ID = new ThreadLocal<>();

    private static final Map<Class<?>, Object> ROOT_AGENTS = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Map<Object, String> INSTANCE_MEMORY_IDS = Collections.synchronizedMap(new IdentityHashMap<>());

    private static volatile Function<Class<?>, Object> rootAgentFactory;
    private static volatile Set<String> agentClassNames = Set.of();
    private static volatile Set<String> perInstanceScopeClassNames = Set.of();

    private AgentClassSupport() {
    }

    /**
     * Called at startup, and again after a restart in dev mode.
     *
     * @param rootAgentFactory creates the LangChain4j agent object of a class-based agent
     * @param agentClassNames the names of the class-based agents
     * @param perInstanceScopeClassNames the names of the class-based agents whose bean instances have their own
     *        memory id
     */
    static void init(Function<Class<?>, Object> rootAgentFactory, Set<String> agentClassNames,
            Set<String> perInstanceScopeClassNames) {
        AgentClassSupport.rootAgentFactory = rootAgentFactory;
        AgentClassSupport.agentClassNames = Set.copyOf(agentClassNames);
        AgentClassSupport.perInstanceScopeClassNames = Set.copyOf(perInstanceScopeClassNames);
        ROOT_AGENTS.clear();
        METHODS.clear();
        INSTANCE_MEMORY_IDS.clear();
    }

    /**
     * The LangChain4j agent object of the given class-based agent, created on first use.
     */
    public static Object rootAgent(Class<?> agentClass) {
        Object agent = ROOT_AGENTS.get(agentClass);
        if (agent == null) {
            // not computeIfAbsent: creating a workflow resolves its sub-agents, which can be class-based agents
            // created through this registry too
            synchronized (ROOT_AGENTS) {
                agent = ROOT_AGENTS.get(agentClass);
                if (agent == null) {
                    if (rootAgentFactory == null) {
                        throw new IllegalStateException("The class-based agents are not initialized");
                    }
                    agent = rootAgentFactory.apply(agentClass);
                    ROOT_AGENTS.put(agentClass, agent);
                }
            }
        }
        return agent;
    }

    /**
     * The implementation of the {@code native} methods of a class-based agent: invokes the method on the agent
     * object of the class. When the bean is invoked by a workflow, through the generated subclass, the call goes
     * back to the agent object that routed it; otherwise the bean was invoked by the application and the call starts
     * on the agent object of the class, with the memory id of the bean instance when it has one.
     *
     * @param agentClass the class declaring the method
     * @param bean the bean instance the method was invoked on
     * @param name the name of the method
     * @param descriptor the JVM descriptor of the method
     * @param args the arguments of the invocation
     */
    public static Object invoke(Class<?> agentClass, Object bean, String name, String descriptor, Object[] args)
            throws Throwable {
        Method method = method(agentClass, name, descriptor);
        Object current = DELEGATE.get();
        if (current != null && current != ROOT && current.getClass().getSuperclass() == agentClass) {
            return invokeOn(current, method, args);
        }
        Object root = rootAgent(agentClass);
        Object previousMemoryId = MEMORY_ID.get();
        DELEGATE.set(ROOT);
        MEMORY_ID.set(perInstanceScopeClassNames.contains(agentClass.getName()) ? instanceMemoryId(bean) : null);
        try {
            return invokeOn(root, method, args);
        } finally {
            restore(DELEGATE, current);
            restore(MEMORY_ID, previousMemoryId);
        }
    }

    /**
     * The implementation of the methods of the generated subclass of a class-based agent: invokes the method on the
     * LangChain4j agent when the subclass was called by its bean, with {@link #invoke}, and routes the call through
     * the bean, so that its CDI interceptors apply, when the subclass was called by a workflow.
     *
     * @param agent the generated subclass instance
     * @param delegate the delegate of the subclass holding the LangChain4j agent
     * @param method the method of the agent type
     * @param args the arguments of the invocation
     */
    public static Object invokeAgentMethod(Object agent, ClassAgentDelegate delegate, Method method, Object[] args)
            throws Throwable {
        Object current = DELEGATE.get();
        if (current == ROOT || current == agent) {
            DELEGATE.remove();
            return delegate.invoke(method, args);
        }
        Object bean = Arc.container().instance(agent.getClass().getSuperclass()).get();
        DELEGATE.set(agent);
        try {
            return invokeOn(bean, method, args);
        } finally {
            restore(DELEGATE, current);
        }
    }

    /**
     * The memory id of the agent bean instance being invoked on the current thread, or {@code null}.
     */
    public static Object currentInstanceMemoryId() {
        return MEMORY_ID.get();
    }

    /**
     * Runs the given invocation of a LangChain4j agent with the given memory id as the one of the current agent bean
     * instance, so that the chat memories created by it belong to that instance.
     */
    static Object withInstanceMemoryId(Object memoryId, AgentInvocation invocation) throws Throwable {
        Object previous = MEMORY_ID.get();
        MEMORY_ID.set(memoryId);
        try {
            return invocation.invoke();
        } finally {
            restore(MEMORY_ID, previous);
        }
    }

    interface AgentInvocation {
        Object invoke() throws Throwable;
    }

    /**
     * Called when a bean instance of a class-based agent is destroyed: evicts its agentic scope and chat memories.
     */
    public static void destroyed(Object bean) {
        String memoryId = INSTANCE_MEMORY_IDS.remove(bean);
        if (memoryId == null) {
            return;
        }
        Class<?> agentClass = agentClass(bean.getClass());
        Object root = agentClass == null ? null : ROOT_AGENTS.get(agentClass);
        if (root != null) {
            delete(root, memoryId);
        }
    }

    /**
     * Deletes from the given root agent the agentic scope with the given memory id, or the chat memory of an AI agent,
     * together with the chat memories that the AI agents of the system created for that memory id: the ones keyed by
     * the memory id itself, deleted by langchain4j-agentic, and the ones that defaulted to it with a per-service
     * suffix, as the Quarkus AI services do. The memory id is the one of a destroyed bean instance, never used again,
     * so the messages stored by those chat memories are deleted, not only the chat memory objects.
     */
    public static void delete(Object rootAgent, Object memoryId) {
        if (rootAgent instanceof AgenticScopeAccess scopeAccess) {
            AgenticScope scope = scopeAccess.getAgenticScope(memoryId);
            if (scope instanceof DefaultAgenticScope defaultScope) {
                for (Object cachedAgent : defaultScope.cachedAgents()) {
                    removeDefaultedChatMemories(cachedAgent, memoryId);
                }
            }
            if (rootAgent instanceof AgentInstance instance) {
                removeDefaultedChatMemories(instance, memoryId);
            }
            scopeAccess.deleteAgenticScope(memoryId);
        } else if (rootAgent instanceof ChatMemoryAccess chatMemoryAccess) {
            removeDefaultedChatMemories(rootAgent, memoryId);
            ChatMemory chatMemory = chatMemoryAccess.getChatMemory(memoryId);
            if (chatMemory != null) {
                chatMemory.clear();
            }
            chatMemoryAccess.evictChatMemory(memoryId);
        }
    }

    private static void removeDefaultedChatMemories(AgentInstance agent, Object memoryId) {
        removeDefaultedChatMemories(agent instanceof AgentExecutor executor ? executor.agent() : agent, memoryId);
        for (AgentInstance subagent : agent.subagents()) {
            removeDefaultedChatMemories(subagent, memoryId);
        }
    }

    private static void removeDefaultedChatMemories(Object agent, Object memoryId) {
        InternalAgent internalAgent = agent instanceof ClassBasedAgent classBased ? classBased.agentDelegate().internalAgent()
                : agent instanceof AbstractQuarkusAgent quarkusAgent ? quarkusAgent.internalAgent()
                        : agent instanceof InternalAgent internal ? internal : null;
        if (internalAgent instanceof AgentInvocationHandler handler
                && handler.aiService() instanceof ChatMemoryRemovable removable) {
            String prefix = memoryId + "#";
            List<Object> ids = new ArrayList<>();
            for (Object id : removable.getAllChatMemoryIds()) {
                if (id instanceof String s && s.startsWith(prefix)) {
                    ids.add(id);
                }
            }
            if (!ids.isEmpty()) {
                removable.remove(ids.toArray());
            }
        }
    }

    private static String instanceMemoryId(Object bean) {
        return INSTANCE_MEMORY_IDS.computeIfAbsent(bean, b -> UUID.randomUUID().toString());
    }

    private static Class<?> agentClass(Class<?> beanClass) {
        for (Class<?> c = beanClass; c != null; c = c.getSuperclass()) {
            if (agentClassNames.contains(c.getName())) {
                return c;
            }
        }
        return null;
    }

    private static Method method(Class<?> agentClass, String name, String descriptor) {
        String key = agentClass.getName() + "#" + name + descriptor;
        Method method = METHODS.get(key);
        if (method == null) {
            method = findMethod(agentClass, name, descriptor);
            method.setAccessible(true);
            METHODS.put(key, method);
        }
        return method;
    }

    private static Method findMethod(Class<?> agentClass, String name, String descriptor) {
        for (Class<?> c = agentClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name) && MethodType
                        .methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString()
                        .equals(descriptor)) {
                    return method;
                }
            }
        }
        throw new IllegalStateException(
                "The method " + name + descriptor + " is not declared by the agent class " + agentClass.getName());
    }

    private static Object invokeOn(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static void restore(ThreadLocal<Object> threadLocal, Object previous) {
        if (previous == null) {
            threadLocal.remove();
        } else {
            threadLocal.set(previous);
        }
    }
}
