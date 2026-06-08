package io.quarkiverse.langchain4j.agentic.runtime.devui;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.control.ActivateRequestContext;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.MonitoredExecution;
import dev.langchain4j.agentic.planner.AgentInstance;
import io.quarkus.arc.Arc;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class AgenticJsonRpcService {

    private static final Logger log = Logger.getLogger(AgenticJsonRpcService.class);

    public JsonArray getRootAgentEntries() {
        List<Object> rootAgents = DevAgentMonitorHolder.rootAgents();
        JsonArray entries = new JsonArray();
        for (int i = 0; i < rootAgents.size(); i++) {
            entries.add(new JsonObject()
                    .put("name", ((AgentInstance) rootAgents.get(i)).name())
                    .put("index", i));
        }
        List<JsonObject> sorted = new java.util.ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            sorted.add(entries.getJsonObject(i));
        }
        sorted.sort(java.util.Comparator.comparing(o -> o.getString("name")));
        return new JsonArray(sorted);
    }

    public JsonObject getTopologyJson(int index) {
        List<Object> rootAgents = DevAgentMonitorHolder.rootAgents();
        if (rootAgents.isEmpty()) {
            return new JsonObject().put("error", "No root agents detected");
        }
        int i = (index >= 0 && index < rootAgents.size()) ? index : 0;
        try {
            AgentInstance rootAgent = (AgentInstance) rootAgents.get(i);
            return serializeAgentTopology(rootAgent);
        } catch (Exception e) {
            log.warn("Failed to generate topology", e);
            return new JsonObject().put("error", "Failed to generate topology: " + e.getMessage());
        }
    }

    private JsonObject serializeAgentTopology(AgentInstance agent) {
        JsonObject node = new JsonObject()
                .put("name", agent.name())
                .put("type", agent.topology() != null ? agent.topology().name() : "AGENT")
                .put("agentId", agent.agentId());

        if (agent.description() != null) {
            node.put("description", agent.description());
        }

        List<AgentInstance> subAgents = agent.subagents();
        if (subAgents != null && !subAgents.isEmpty()) {
            JsonArray children = new JsonArray();
            for (AgentInstance sub : subAgents) {
                children.add(serializeAgentTopology(sub));
            }
            node.put("subAgents", children);
        }
        return node;
    }

    public JsonObject getExecutionReportJson(int index) {
        List<AgentMonitor> monitors = DevAgentMonitorHolder.monitors();
        if (monitors.isEmpty()) {
            return new JsonObject().put("error", "No execution data available");
        }
        int i = (index >= 0 && index < monitors.size()) ? index : 0;
        try {
            AgentMonitor monitor = monitors.get(i);
            JsonArray executions = new JsonArray();

            for (MonitoredExecution exec : monitor.successfulExecutions()) {
                executions.add(serializeExecution(exec, "success"));
            }
            for (MonitoredExecution exec : monitor.failedExecutions()) {
                executions.add(serializeExecution(exec, "failed"));
            }
            for (MonitoredExecution exec : monitor.ongoingExecutions().values()) {
                executions.add(serializeExecution(exec, "ongoing"));
            }

            return new JsonObject().put("executions", executions);
        } catch (Exception e) {
            log.warn("Failed to generate execution report", e);
            return new JsonObject().put("error", "Failed: " + e.getMessage());
        }
    }

    private JsonObject serializeExecution(MonitoredExecution exec, String status) {
        JsonObject obj = new JsonObject()
                .put("memoryId", String.valueOf(exec.memoryId()))
                .put("status", status)
                .put("topLevel", serializeInvocation(exec.topLevelInvocations()));
        if (exec.hasError()) {
            obj.put("error", exec.error().error().getMessage());
        }
        return obj;
    }

    private JsonObject serializeInvocation(AgentInvocation inv) {
        JsonObject obj = new JsonObject()
                .put("agentName", inv.agent().name())
                .put("startTime", inv.startTime().toString());
        if (inv.done()) {
            obj.put("duration", inv.duration().toMillis());
            obj.put("tokenCount", inv.totalTokenCount());
            obj.put("output", inv.output() != null ? String.valueOf(inv.output()) : null);
        } else {
            obj.put("status", "in_progress");
        }
        if (inv.iterationIndex() >= 0) {
            obj.put("iterationIndex", inv.iterationIndex());
        }

        if (!inv.toolExecutions().isEmpty()) {
            JsonArray tools = new JsonArray();
            for (var toolExec : inv.toolExecutions()) {
                tools.add(new JsonObject()
                        .put("name", toolExec.request().name())
                        .put("arguments", toolExec.request().arguments())
                        .put("result", toolExec.result()));
            }
            obj.put("toolExecutions", tools);
        }

        if (!inv.nestedInvocations().isEmpty()) {
            JsonArray nested = new JsonArray();
            for (AgentInvocation sub : inv.nestedInvocations()) {
                nested.add(serializeInvocation(sub));
            }
            obj.put("nestedInvocations", nested);
        }
        return obj;
    }

    @ActivateRequestContext
    public JsonObject invokeAgent(String agentClassName, String methodName, String inputJson) {
        try {
            Class<?> agentClass = Class.forName(agentClassName, true, Thread.currentThread().getContextClassLoader());
            Object agent = Arc.container().select(agentClass).get();

            Method targetMethod = null;
            for (Method m : agentClass.getMethods()) {
                if (m.getName().equals(methodName)) {
                    targetMethod = m;
                    break;
                }
            }
            if (targetMethod == null) {
                return new JsonObject()
                        .put("success", false)
                        .put("error", "Method '" + methodName + "' not found on " + agentClassName);
            }

            Object[] args = resolveArguments(targetMethod, inputJson);
            Object result = targetMethod.invoke(agent, args);

            return new JsonObject()
                    .put("success", true)
                    .put("result", result != null ? String.valueOf(result) : "null");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return new JsonObject()
                    .put("success", false)
                    .put("error", cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }

    private Object[] resolveArguments(Method method, String inputJson) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            return new Object[0];
        }

        JsonObject inputs = (inputJson != null && !inputJson.isBlank()) ? new JsonObject(inputJson) : new JsonObject();
        java.lang.reflect.Parameter[] params = method.getParameters();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> type = paramTypes[i];
            String paramName = params[i].getName();

            if (type.getName().equals("dev.langchain4j.agentic.scope.AgenticScope")) {
                args[i] = null;
                continue;
            }

            if (params[i].isAnnotationPresent(dev.langchain4j.service.MemoryId.class)
                    || paramName.equals("memoryId")) {
                args[i] = inputs.containsKey(paramName) ? inputs.getString(paramName) : UUID.randomUUID().toString();
                continue;
            }

            if (!inputs.containsKey(paramName)) {
                args[i] = null;
                continue;
            }

            if (type == String.class) {
                args[i] = inputs.getString(paramName);
            } else if (type == int.class || type == Integer.class) {
                args[i] = inputs.getInteger(paramName);
            } else if (type == long.class || type == Long.class) {
                args[i] = inputs.getLong(paramName);
            } else if (type == double.class || type == Double.class) {
                args[i] = inputs.getDouble(paramName);
            } else if (type == boolean.class || type == Boolean.class) {
                args[i] = inputs.getBoolean(paramName);
            } else {
                args[i] = inputs.getString(paramName);
            }
        }
        return args;
    }
}
