package io.quarkiverse.langchain4j.agentic.runtime.devui;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.context.control.ActivateRequestContext;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.observability.MonitoredExecution;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.workflow.ConditionalAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentInstance;
import dev.langchain4j.agentic.workflow.LoopAgentInstance;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.tool.ToolExecution;
import io.quarkiverse.langchain4j.agentic.runtime.devui.DevAgentMonitorHolder.MonitoredRoot;
import io.quarkus.arc.Arc;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Dev UI backend of the agentic pages. Topology and executions are serialized as JSON carrying the same data the
 * LangChain4j {@link HtmlReportGenerator} renders, and the generator's own HTML is exposed for the canonical report.
 */
public class AgenticJsonRpcService {

    private static final Logger log = Logger.getLogger(AgenticJsonRpcService.class);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final String[] KEY_PALETTE = {
            "#e63946", "#457b9d", "#2a9d8f", "#e9c46a", "#7b2d8e",
            "#f4a261", "#264653", "#d62828", "#6a994e", "#bc6c25"
    };

    public JsonArray getRootAgentEntries() {
        List<MonitoredRoot> roots = DevAgentMonitorHolder.roots();
        List<JsonObject> entries = new ArrayList<>();
        for (int i = 0; i < roots.size(); i++) {
            AgentInstance agent = roots.get(i).agent();
            entries.add(new JsonObject()
                    .put("index", i)
                    .put("name", agent.name())
                    .put("typeName", agent.type() != null ? agent.type().getSimpleName() : agent.name())
                    .put("className", agent.type() != null ? agent.type().getName() : null)
                    .put("topology", agent.topology().name()));
        }
        entries.sort(ByTypeName.INSTANCE);
        return new JsonArray(new ArrayList<>(entries));
    }

    public JsonObject getTopology(int index) {
        MonitoredRoot root = rootAt(index);
        if (root == null) {
            return new JsonObject().put("error", "No root agents detected");
        }
        try {
            AgentInstance rootAgent = root.agent();
            Map<String, String> keyColors = collectStateKeyColors(rootAgent);
            Set<AgenticSystemTopology> topologies = new LinkedHashSet<>();
            collectTopologies(rootAgent, topologies);

            JsonArray legend = new JsonArray();
            for (AgenticSystemTopology topology : topologies) {
                legend.add(topology.name());
            }
            JsonArray dataFlow = new JsonArray();
            for (Map.Entry<String, String> keyColor : keyColors.entrySet()) {
                dataFlow.add(new JsonObject().put("key", keyColor.getKey()).put("color", keyColor.getValue()));
            }
            return new JsonObject()
                    .put("legend", legend)
                    .put("dataFlow", dataFlow)
                    .put("root", serializeNode(rootAgent, null));
        } catch (Exception e) {
            log.warn("Failed to generate topology", e);
            return new JsonObject().put("error", "Failed to generate topology: " + e.getMessage());
        }
    }

    public JsonObject getExecutions(int index) {
        MonitoredRoot root = rootAt(index);
        if (root == null) {
            return new JsonObject().put("error", "No root agents detected");
        }
        try {
            AgentMonitor monitor = root.monitor();
            String rootAgentId = root.agent().agentId();
            JsonArray sessions = new JsonArray();
            for (Object memoryId : monitor.allMemoryIds()) {
                JsonArray executions = new JsonArray();
                int success = 0;
                int failed = 0;
                int running = 0;
                for (MonitoredExecution execution : monitor.allExecutionsFor(memoryId)) {
                    if (!rootAgentId.equals(execution.topLevelInvocations().agent().agentId())) {
                        continue;
                    }
                    String status = statusOf(execution);
                    switch (status) {
                        case "ok" -> success++;
                        case "fail" -> failed++;
                        default -> running++;
                    }
                    executions.add(serializeExecution(execution, status));
                }
                if (!executions.isEmpty()) {
                    sessions.add(new JsonObject()
                            .put("memoryId", String.valueOf(memoryId))
                            .put("runs", executions.size())
                            .put("success", success)
                            .put("failed", failed)
                            .put("running", running)
                            .put("executions", executions));
                }
            }
            return new JsonObject()
                    .put("nowMillis", epochMillis(LocalDateTime.now()))
                    .put("sessions", sessions);
        } catch (Exception e) {
            log.warn("Failed to generate execution report", e);
            return new JsonObject().put("error", "Failed to generate execution report: " + e.getMessage());
        }
    }

    public String getTopologyReportHtml(int index) {
        MonitoredRoot root = rootAt(index);
        if (root == null) {
            return "<html><body><p>No root agents detected.</p></body></html>";
        }
        return HtmlReportGenerator.generateTopology(root.agent());
    }

    public String getExecutionReportHtml(int index) {
        MonitoredRoot root = rootAt(index);
        if (root == null) {
            return "<html><body><p>No root agents detected.</p></body></html>";
        }
        return HtmlReportGenerator.generateExecution(root.monitor());
    }

    private static MonitoredRoot rootAt(int index) {
        List<MonitoredRoot> roots = DevAgentMonitorHolder.roots();
        if (roots.isEmpty()) {
            return null;
        }
        return index >= 0 && index < roots.size() ? roots.get(index) : roots.get(0);
    }

    private JsonObject serializeNode(AgentInstance agent, String condition) {
        JsonObject node = new JsonObject()
                .put("agentId", agent.agentId())
                .put("name", agent.name())
                .put("topology", agent.topology().name())
                .put("async", agent.async())
                .put("optional", agent.optional());
        if (condition != null && !condition.isEmpty()) {
            node.put("condition", condition);
        }
        if (agent.type() != null) {
            node.put("typeName", agent.type().getSimpleName());
            node.put("className", agent.type().getName());
        }
        if (agent.description() != null && !agent.description().isEmpty()) {
            node.put("description", agent.description());
        }
        if (agent.outputType() != null) {
            node.put("outputType", simpleTypeName(agent.outputType()));
        }
        if (agent.plannerType() != null) {
            node.put("plannerType", agent.plannerType().getSimpleName());
        }
        if (agent.outputKey() != null && !agent.outputKey().isEmpty()) {
            node.put("outputKey", agent.outputKey());
        }
        JsonArray arguments = new JsonArray();
        if (agent.arguments() != null) {
            for (AgentArgument argument : agent.arguments()) {
                JsonObject json = new JsonObject()
                        .put("name", argument.name())
                        .put("optional", argument.isOptional());
                if (argument.type() != null) {
                    json.put("type", simpleTypeName(argument.type()));
                }
                arguments.add(json);
            }
        }
        node.put("arguments", arguments);
        if (agent.topology() == AgenticSystemTopology.LOOP) {
            LoopAgentInstance loop = agent.as(LoopAgentInstance.class);
            JsonObject loopJson = new JsonObject()
                    .put("maxIterations", loop.maxIterations())
                    .put("testExitAtLoopEnd", loop.testExitAtLoopEnd());
            if (loop.exitCondition() != null && !loop.exitCondition().isEmpty()) {
                loopJson.put("exitCondition", loop.exitCondition());
            }
            node.put("loop", loopJson);
        }
        JsonArray subagents = new JsonArray();
        List<AgentInstance> children = agent.subagents();
        if (children != null && !children.isEmpty()) {
            Map<String, String> conditions = conditionsOf(agent);
            for (AgentInstance child : children) {
                subagents.add(serializeNode(child, conditions.get(child.agentId())));
            }
        }
        node.put("subagents", subagents);
        return node;
    }

    private static Map<String, String> conditionsOf(AgentInstance agent) {
        if (agent.topology() != AgenticSystemTopology.ROUTER) {
            return Map.of();
        }
        ConditionalAgentInstance router;
        try {
            router = agent.as(ConditionalAgentInstance.class);
        } catch (ClassCastException e) {
            return Map.of();
        }
        Map<String, String> conditions = new LinkedHashMap<>();
        for (ConditionalAgent conditionalAgent : router.conditionalSubagents()) {
            if (conditionalAgent.condition() != null) {
                for (AgentInstance child : conditionalAgent.agentInstances()) {
                    conditions.put(child.agentId(), conditionalAgent.condition());
                }
            }
        }
        return conditions;
    }

    private static void collectTopologies(AgentInstance agent, Set<AgenticSystemTopology> topologies) {
        topologies.add(agent.topology());
        if (agent.subagents() != null) {
            for (AgentInstance child : agent.subagents()) {
                collectTopologies(child, topologies);
            }
        }
    }

    private static Map<String, String> collectStateKeyColors(AgentInstance root) {
        Set<String> keys = new LinkedHashSet<>();
        collectKeys(root, keys);
        Map<String, String> colors = new LinkedHashMap<>();
        int i = 0;
        for (String key : keys) {
            colors.put(key, KEY_PALETTE[i % KEY_PALETTE.length]);
            i++;
        }
        return colors;
    }

    private static void collectKeys(AgentInstance agent, Set<String> keys) {
        if (agent.outputKey() != null && !agent.outputKey().isEmpty()) {
            keys.add(agent.outputKey());
        }
        if (agent.arguments() != null) {
            for (AgentArgument argument : agent.arguments()) {
                if (argument.name() != null) {
                    keys.add(argument.name());
                }
            }
        }
        if (agent.subagents() != null) {
            for (AgentInstance child : agent.subagents()) {
                collectKeys(child, keys);
            }
        }
    }

    private static String statusOf(MonitoredExecution execution) {
        if (execution.hasError()) {
            return "fail";
        }
        if (execution.done()) {
            return "ok";
        }
        return "run";
    }

    private JsonObject serializeExecution(MonitoredExecution execution, String status) {
        JsonObject json = new JsonObject()
                .put("status", status)
                .put("topLevel", serializeInvocation(execution.topLevelInvocations()));
        if (execution.hasError()) {
            Throwable error = execution.error().error();
            JsonObject errorJson = new JsonObject()
                    .put("agentName", execution.error().agentName())
                    .put("agentId", execution.error().agentId())
                    .put("message", String.valueOf(error.getMessage()))
                    .put("type", error.getClass().getName());
            Throwable rootCause = rootCause(error);
            if (rootCause != error) {
                errorJson.put("rootCause", rootCause.getClass().getSimpleName() + ": " + rootCause.getMessage());
            }
            json.put("error", errorJson);
        }
        return json;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private JsonObject serializeInvocation(AgentInvocation invocation) {
        AgentInstance agent = invocation.agent();
        JsonObject json = new JsonObject()
                .put("agentId", agent.agentId())
                .put("name", agent.name())
                .put("topology", agent.topology().name())
                .put("done", invocation.done())
                .put("startMillis", epochMillis(invocation.startTime()))
                .put("startTime", invocation.startTime().format(TIME_FMT))
                .put("iterationIndex", invocation.iterationIndex());
        if (invocation.done()) {
            json.put("finishMillis", epochMillis(invocation.finishTime()))
                    .put("finishTime", invocation.finishTime().format(TIME_FMT))
                    .put("durationMillis", invocation.duration().toMillis())
                    .put("totalTokenCount", invocation.totalTokenCount())
                    .put("output", invocation.output() != null ? String.valueOf(invocation.output()) : null);
        }
        JsonObject inputs = new JsonObject();
        if (invocation.inputs() != null) {
            for (Map.Entry<String, Object> input : invocation.inputs().entrySet()) {
                inputs.put(input.getKey(), input.getValue() != null ? String.valueOf(input.getValue()) : null);
            }
        }
        json.put("inputs", inputs);

        JsonArray tools = new JsonArray();
        for (ToolExecution toolExecution : invocation.toolExecutions()) {
            JsonObject tool = new JsonObject()
                    .put("name", toolExecution.request().name())
                    .put("arguments", toolExecution.request().arguments())
                    .put("result", toolExecution.result())
                    .put("failed", toolExecution.hasFailed());
            if (toolExecution.startTime() != null && toolExecution.duration() != null) {
                tool.put("startMillis", epochMillis(toolExecution.startTime()))
                        .put("durationMillis", toolExecution.duration().toMillis());
            }
            tools.add(tool);
        }
        json.put("toolExecutions", tools);

        JsonArray nested = new JsonArray();
        for (AgentInvocation nestedInvocation : invocation.nestedInvocations()) {
            nested.add(serializeInvocation(nestedInvocation));
        }
        json.put("nestedInvocations", nested);
        return json;
    }

    private static final class ByTypeName implements Comparator<JsonObject> {

        private static final ByTypeName INSTANCE = new ByTypeName();

        @Override
        public int compare(JsonObject first, JsonObject second) {
            return first.getString("typeName").compareTo(second.getString("typeName"));
        }
    }

    private static long epochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String simpleTypeName(Type type) {
        if (type instanceof Class<?> c) {
            return c.getSimpleName();
        }
        String name = type.getTypeName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
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
        Parameter[] params = method.getParameters();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> type = paramTypes[i];
            String paramName = params[i].getName();

            if (AgenticScope.class.isAssignableFrom(type)) {
                args[i] = null;
                continue;
            }

            if (params[i].isAnnotationPresent(MemoryId.class) || paramName.equals("memoryId")) {
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
