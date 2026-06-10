package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import dev.langchain4j.agentic.agent.ChatMessagesAccess;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.service.memory.ChatMemoryAccess;

public abstract class AbstractQuarkusAgent implements InternalAgent, AgenticScopeOwner {

    protected InternalAgent agent;
    private Object agentProxy;

    private static final Method METHOD_withAgenticScope;
    private static final Method METHOD_registry;
    private static final Method METHOD_toString;
    private static final Method METHOD_hashCode;

    private static final Method METHOD_getChatMemory;
    private static final Method METHOD_evictChatMemory;
    private static final Method METHOD_lastUserMessage;
    private static final Method METHOD_lastChatRequest;
    private static final Method METHOD_lastChatResponse;
    private static final Method METHOD_removeLastResponseEvent;
    private static final Method METHOD_getEventClass;
    private static final Method METHOD_onEvent;
    private static final Method METHOD_getAgenticScope;
    private static final Method METHOD_evictAgenticScope;

    static {
        try {
            METHOD_withAgenticScope = AgenticScopeOwner.class.getMethod("withAgenticScope", DefaultAgenticScope.class);
            METHOD_registry = AgenticScopeOwner.class.getMethod("registry");
            METHOD_toString = Object.class.getMethod("toString");
            METHOD_hashCode = Object.class.getMethod("hashCode");

            METHOD_getChatMemory = ChatMemoryAccess.class.getMethod("getChatMemory", Object.class);
            METHOD_evictChatMemory = ChatMemoryAccess.class.getMethod("evictChatMemory", Object.class);
            METHOD_lastUserMessage = ChatMessagesAccess.class.getMethod("lastUserMessage", Object.class);
            METHOD_lastChatRequest = ChatMessagesAccess.class.getMethod("lastChatRequest", Object.class);
            METHOD_lastChatResponse = ChatMessagesAccess.class.getMethod("lastChatResponse", Object.class);
            METHOD_removeLastResponseEvent = ChatMessagesAccess.class.getMethod("removeLastResponseEvent", Object.class);
            METHOD_getEventClass = AiServiceListener.class.getMethod("getEventClass");
            METHOD_onEvent = AiServiceListener.class.getMethod("onEvent", AiServiceEvent.class);
            METHOD_getAgenticScope = AgenticScopeAccess.class.getMethod("getAgenticScope", Object.class);
            METHOD_evictAgenticScope = AgenticScopeAccess.class.getMethod("evictAgenticScope", Object.class);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    protected AbstractQuarkusAgent() {
    }

    protected AbstractQuarkusAgent(InternalAgent agent) {
        this.agent = agent;
    }

    public void setAgentProxy(Object proxy) {
        this.agentProxy = proxy;
    }

    private Object invokeOnAgent(Method method, Object[] args) throws Throwable {
        return ((InvocationHandler) agent).invoke(this, method, args);
    }

    // InternalAgent / AgentInstance delegation

    @Override
    public Class<?> type() {
        return agent.type();
    }

    @Override
    public Class<? extends Planner> plannerType() {
        return agent.plannerType();
    }

    @Override
    public String name() {
        return agent.name();
    }

    @Override
    public String agentId() {
        return agent.agentId();
    }

    @Override
    public String description() {
        return agent.description();
    }

    @Override
    public Type outputType() {
        return agent.outputType();
    }

    @Override
    public String outputKey() {
        return agent.outputKey();
    }

    @Override
    public boolean async() {
        return agent.async();
    }

    @Override
    public boolean optional() {
        return agent.optional();
    }

    @Override
    public List<AgentArgument> arguments() {
        return agent.arguments();
    }

    @Override
    public AgentInstance parent() {
        return agent.parent();
    }

    @Override
    public List<AgentInstance> subagents() {
        return agent.subagents();
    }

    @Override
    public AgenticSystemTopology topology() {
        return agent.topology();
    }

    @Override
    public void setParent(InternalAgent parent) {
        agent.setParent(parent);
    }

    @Override
    public void registerInheritedParentListener(AgentListener listener) {
        agent.registerInheritedParentListener(listener);
    }

    @Override
    public void appendId(String suffix) {
        agent.appendId(suffix);
    }

    @Override
    public void setAgentId(String id) {
        agent.setAgentId(id);
    }

    @Override
    public AgentListener listener() {
        return agent.listener();
    }

    @Override
    public boolean allowStreamingOutput() {
        return agent.allowStreamingOutput();
    }

    @Override
    public boolean allowChatMemory() {
        return agent.allowChatMemory();
    }

    // AgenticScopeOwner delegation

    @Override
    public AgenticScopeOwner withAgenticScope(DefaultAgenticScope scope) {
        try {
            Object result = invokeOnAgent(METHOD_withAgenticScope, new Object[] { scope });
            return (AgenticScopeOwner) AgentClassCreateInfo.resolveAgentProxy(result, this, agentProxy);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AgenticScopeRegistry registry() {
        try {
            return (AgenticScopeRegistry) invokeOnAgent(METHOD_registry, null);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        if (agent == null) {
            return super.toString();
        }
        try {
            return (String) invokeOnAgent(METHOD_toString, null);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int hashCode() {
        if (agent == null) {
            return super.hashCode();
        }
        try {
            return (int) invokeOnAgent(METHOD_hashCode, null);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // ChatMemoryAccess delegation (leaf agents inherit via interface declared on subclass)

    public ChatMemory getChatMemory(Object memoryId) {
        try {
            return (ChatMemory) invokeOnAgent(METHOD_getChatMemory, new Object[] { memoryId });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public boolean evictChatMemory(Object memoryId) {
        try {
            return (boolean) invokeOnAgent(METHOD_evictChatMemory, new Object[] { memoryId });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // ChatMessagesAccess delegation

    public UserMessage lastUserMessage(Object memoryId) {
        try {
            return (UserMessage) invokeOnAgent(METHOD_lastUserMessage, new Object[] { memoryId });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public ChatRequest lastChatRequest(Object memoryId) {
        try {
            return (ChatRequest) invokeOnAgent(METHOD_lastChatRequest, new Object[] { memoryId });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public ChatResponse lastChatResponse(Object memoryId) {
        try {
            return (ChatResponse) invokeOnAgent(METHOD_lastChatResponse, new Object[] { memoryId });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void removeLastResponseEvent(Object memoryId) {
        try {
            invokeOnAgent(METHOD_removeLastResponseEvent, new Object[] { memoryId });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // AiServiceListener delegation

    public Class<?> getEventClass() {
        try {
            return (Class<?>) invokeOnAgent(METHOD_getEventClass, null);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void onEvent(AiServiceEvent event) {
        try {
            invokeOnAgent(METHOD_onEvent, new Object[] { event });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // AgenticScopeAccess delegation (composite agents inherit via interface declared on subclass)

    public AgenticScope getAgenticScope(Object memoryId) {
        try {
            return (AgenticScope) invokeOnAgent(METHOD_getAgenticScope, new Object[] { memoryId });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public boolean evictAgenticScope(Object memoryId) {
        try {
            return (boolean) invokeOnAgent(METHOD_evictAgenticScope, new Object[] { memoryId });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
