package io.quarkiverse.langchain4j.agentic.runtime.devui;

import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.MonitoredExecution;
import dev.langchain4j.agentic.planner.AgentInstance;
import io.quarkus.arc.Arc;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class AgenticJsonRpcService {

    private static final Logger log = Logger.getLogger(AgenticJsonRpcService.class);

    @Inject
    DevModeAgentMonitor monitor;

    public JsonArray getRootAgentEntries() {
        List<Object> rootAgents = DevAgentMonitorHolder.rootAgents();
        JsonArray entries = new JsonArray();
        for (int i = 0; i < rootAgents.size(); i++) {
            AgentInstance rootAgent = (AgentInstance) rootAgents.get(i);
            entries.add(new JsonObject()
                    .put("name", agentLabel(rootAgent))
                    .put("type", agentTypeLabel(rootAgent))
                    .put("index", i));
        }
        List<JsonObject> sorted = new java.util.ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            sorted.add(entries.getJsonObject(i));
        }
        sorted.sort(java.util.Comparator.comparing(o -> o.getString("name")));
        return new JsonArray(sorted);
    }

    public JsonObject getTopologyMermaid(int index) {
        List<Object> rootAgents = DevAgentMonitorHolder.rootAgents();
        if (rootAgents.isEmpty()) {
            return new JsonObject().put("error", "No root agents detected");
        }
        int i = (index >= 0 && index < rootAgents.size()) ? index : 0;
        try {
            AgentInstance rootAgent = (AgentInstance) rootAgents.get(i);
            return new JsonObject().put("mermaid", buildMermaid(rootAgent));
        } catch (Exception e) {
            log.warn("Failed to generate topology", e);
            return new JsonObject().put("error", "Failed to generate topology: " + e.getMessage());
        }
    }

    private String buildMermaid(AgentInstance root) {
        Map<AgentInstance, String> ids = new LinkedHashMap<>();
        Map<String, String> producers = new LinkedHashMap<>();
        List<String[]> consumers = new ArrayList<>();
        Set<String> usedTypes = new LinkedHashSet<>();

        StringBuilder sb = new StringBuilder("flowchart TD\n");
        appendMermaidNodes(root, sb, ids, producers, consumers, usedTypes);

        for (Map.Entry<AgentInstance, String> entry : ids.entrySet()) {
            List<AgentInstance> children = entry.getKey().subagents();
            if (children != null) {
                for (AgentInstance child : children) {
                    sb.append("  ").append(entry.getValue()).append(" --> ").append(ids.get(child)).append('\n');
                }
            }
        }

        for (String[] consumer : consumers) {
            String producerId = producers.get(consumer[0]);
            if (producerId != null && !producerId.equals(consumer[1])) {
                sb.append("  ").append(producerId).append(" -. \"").append(mermaidText(consumer[0]))
                        .append("\" .-> ").append(consumer[1]).append('\n');
            }
        }

        for (String type : usedTypes) {
            sb.append("  classDef ").append(topologyCss(type)).append(" fill:").append(topologyColor(type))
                    .append(",color:#fff,stroke:").append(topologyColor(type)).append('\n');
        }
        for (Map.Entry<AgentInstance, String> entry : ids.entrySet()) {
            sb.append("  class ").append(entry.getValue()).append(' ')
                    .append(topologyCss(topologyName(entry.getKey()))).append('\n');
        }
        return sb.toString();
    }

    private void appendMermaidNodes(AgentInstance agent, StringBuilder sb, Map<AgentInstance, String> ids,
            Map<String, String> producers, List<String[]> consumers, Set<String> usedTypes) {
        String id = "n" + ids.size();
        ids.put(agent, id);
        String type = topologyName(agent);
        usedTypes.add(type);
        sb.append("  ").append(id).append("[\"").append(agentTypeLabel(agent)).append("<br/>")
                .append(mermaidText(agentLabel(agent))).append("\"]\n");

        if (agent.outputKey() != null && !agent.outputKey().isEmpty()) {
            producers.put(agent.outputKey(), id);
        }
        if (agent.arguments() != null) {
            for (var arg : agent.arguments()) {
                consumers.add(new String[] { arg.name(), id });
            }
        }
        if (agent.subagents() != null) {
            for (AgentInstance child : agent.subagents()) {
                appendMermaidNodes(child, sb, ids, producers, consumers, usedTypes);
            }
        }
    }

    public JsonObject getExecutionReportJson(int index) {
        try {
            AgentInstance root = rootAt(index);
            String rootClassName = root != null && root.type() != null ? root.type().getName() : null;
            Set<String> rootScope = subtreeTypes(root);
            JsonArray executions = new JsonArray();

            for (MonitoredExecution exec : monitor.successfulExecutions()) {
                if (belongsToRoot(exec, rootClassName, rootScope)) {
                    executions.add(serializeExecution(exec, "success"));
                }
            }
            for (MonitoredExecution exec : monitor.failedExecutions()) {
                if (belongsToRoot(exec, rootClassName, rootScope)) {
                    executions.add(serializeExecution(exec, "failed"));
                }
            }
            for (MonitoredExecution exec : monitor.ongoingExecutions().values()) {
                if (belongsToRoot(exec, rootClassName, rootScope)) {
                    executions.add(serializeExecution(exec, "ongoing"));
                }
            }

            return new JsonObject().put("executions", executions);
        } catch (Exception e) {
            log.warn("Failed to generate execution report", e);
            return new JsonObject().put("error", "Failed: " + e.getMessage());
        }
    }

    private AgentInstance rootAt(int index) {
        List<Object> rootAgents = DevAgentMonitorHolder.rootAgents();
        if (index < 0 || index >= rootAgents.size()) {
            return null;
        }
        return (AgentInstance) rootAgents.get(index);
    }

    private Set<String> subtreeTypes(AgentInstance root) {
        if (root == null) {
            return null;
        }
        Set<String> types = new LinkedHashSet<>();
        collectSubtreeTypes(root, types);
        return types;
    }

    private void collectSubtreeTypes(AgentInstance agent, Set<String> types) {
        if (agent.type() != null) {
            types.add(agent.type().getName());
        }
        if (agent.subagents() != null) {
            for (AgentInstance child : agent.subagents()) {
                collectSubtreeTypes(child, types);
            }
        }
    }

    private boolean belongsToRoot(MonitoredExecution exec, String rootClassName, Set<String> rootScope) {
        String mappedRoot = monitor.rootClassNameFor(exec.memoryId());
        if (mappedRoot != null) {
            return mappedRoot.equals(rootClassName);
        }
        return rootScope == null || invocationInScope(exec.topLevelInvocations(), rootScope);
    }

    private boolean invocationInScope(AgentInvocation inv, Set<String> rootScope) {
        if (inv.agent() != null && inv.agent().type() != null
                && rootScope.contains(inv.agent().type().getName())) {
            return true;
        }
        for (AgentInvocation sub : inv.nestedInvocations()) {
            if (invocationInScope(sub, rootScope)) {
                return true;
            }
        }
        return false;
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
                .put("agentName", agentLabel(inv.agent()))
                .put("type", agentTypeLabel(inv.agent()))
                .put("startMillis", epochMillis(inv.startTime()));
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
        if (inv.inputs() != null && !inv.inputs().isEmpty()) {
            JsonObject inputs = new JsonObject();
            for (Map.Entry<String, Object> input : inv.inputs().entrySet()) {
                inputs.put(input.getKey(), input.getValue() != null ? String.valueOf(input.getValue()) : null);
            }
            obj.put("inputs", inputs);
        }

        if (!inv.toolExecutions().isEmpty()) {
            JsonArray tools = new JsonArray();
            for (var toolExec : inv.toolExecutions()) {
                JsonObject tool = new JsonObject()
                        .put("name", toolExec.request().name())
                        .put("arguments", toolExec.request().arguments())
                        .put("result", toolExec.result())
                        .put("failed", toolExec.hasFailed());
                if (toolExec.startTime() != null && toolExec.duration() != null) {
                    tool.put("startMillis", epochMillis(toolExec.startTime()));
                    tool.put("duration", toolExec.duration().toMillis());
                }
                tools.add(tool);
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

    private static long epochMillis(java.time.LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String topologyName(AgentInstance agent) {
        return agent.topology() != null ? agent.topology().name() : "AI_AGENT";
    }

    private static String agentTypeLabel(AgentInstance agent) {
        String annotationType = DevAgentMonitorHolder.agentTypesByClassName.get(agent.type().getName());
        return annotationType != null ? annotationType : topologyLabel(topologyName(agent));
    }

    private static String agentLabel(AgentInstance agent) {
        if (agent.type() == null) {
            return agent.name();
        }
        String className = agent.type().getName();
        int cut = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
        return cut >= 0 ? className.substring(cut + 1) : className;
    }

    private static String mermaidText(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\"", "'").replace("\n", " ").replace("[", "(").replace("]", ")");
    }

    private static String topologyLabel(String type) {
        return switch (type) {
            case "NON_AI_AGENT" -> "Action";
            case "HUMAN_IN_THE_LOOP" -> "Human";
            case "SEQUENCE" -> "Sequence";
            case "PARALLEL" -> "Parallel";
            case "LOOP" -> "Loop";
            case "ROUTER" -> "Router";
            case "STAR" -> "Star";
            default -> "AI";
        };
    }

    private static String topologyCss(String type) {
        return switch (type) {
            case "NON_AI_AGENT" -> "nonai";
            case "HUMAN_IN_THE_LOOP" -> "human";
            case "SEQUENCE" -> "seq";
            case "PARALLEL" -> "par";
            case "LOOP" -> "loop";
            case "ROUTER" -> "rtr";
            case "STAR" -> "star";
            default -> "ai";
        };
    }

    private static String topologyColor(String type) {
        return switch (type) {
            case "NON_AI_AGENT" -> "#6b7280";
            case "HUMAN_IN_THE_LOOP" -> "#d97706";
            case "SEQUENCE" -> "#0891b2";
            case "PARALLEL" -> "#3b82f6";
            case "LOOP" -> "#7c3aed";
            case "ROUTER" -> "#dc2626";
            case "STAR" -> "#ca8a04";
            default -> "#2e8555";
        };
    }

    @ActivateRequestContext
    public JsonObject invokeAgent(String agentClassName, String methodName, String inputJson) {
        if (!DevAgentMonitorHolder.allowedAgentClassNames.contains(agentClassName)) {
            return new JsonObject()
                    .put("success", false)
                    .put("error", "Unknown agent class: " + agentClassName);
        }
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
            Object result;
            monitor.markPendingRoot(agentClassName);
            try {
                result = targetMethod.invoke(agent, args);
            } finally {
                monitor.clearPendingRoot();
            }

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
