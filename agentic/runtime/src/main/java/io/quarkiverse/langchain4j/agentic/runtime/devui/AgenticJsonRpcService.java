package io.quarkiverse.langchain4j.agentic.runtime.devui;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.control.ActivateRequestContext;

import org.jboss.logging.Logger;

import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
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

    public String getTopologyHtml(int index) {
        List<Object> rootAgents = DevAgentMonitorHolder.rootAgents();
        if (rootAgents.isEmpty()) {
            return "<html><body><p>No root agents detected.</p></body></html>";
        }
        int i = (index >= 0 && index < rootAgents.size()) ? index : 0;
        try {
            return HtmlReportGenerator.generateTopology(rootAgents.get(i));
        } catch (Exception e) {
            log.warn("Failed to generate topology", e);
            return "<html><body><p>Failed to generate topology.</p></body></html>";
        }
    }

    public String getExecutionReportHtml(int index) {
        List<AgentMonitor> monitors = DevAgentMonitorHolder.monitors();
        if (monitors.isEmpty()) {
            return "<html><body><p>No execution data available. Invoke an agent first.</p></body></html>";
        }
        int i = (index >= 0 && index < monitors.size()) ? index : 0;
        try {
            return HtmlReportGenerator.generateExecution(monitors.get(i));
        } catch (Exception e) {
            log.warn("Failed to generate execution report", e);
            return "<html><body><p>Failed to generate execution report.</p></body></html>";
        }
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
