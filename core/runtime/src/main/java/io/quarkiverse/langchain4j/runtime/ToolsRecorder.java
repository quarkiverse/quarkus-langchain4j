package io.quarkiverse.langchain4j.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutor;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutor;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutorFactory;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ToolsRecorder {

    private static final Logger log = Logger.getLogger(ToolsRecorder.class);

    // the key is the class' name
    private static final Map<String, List<ToolMethodCreateInfo>> metadata = new ConcurrentHashMap<>();

    public void setMetadata(Map<String, List<ToolMethodCreateInfo>> metadata) {
        ToolsRecorder.metadata.putAll(metadata);
    }

    public static Map<String, List<ToolMethodCreateInfo>> getMetadata() {
        return metadata;
    }

    public static void clearMetadata() {
        metadata.clear();
    }

    /**
     * Builds {@link AiServiceTool} instances for every tool-bearing object, carrying the per-tool
     * {@link ReturnBehavior} from build-time scanning. Returning a {@code List<AiServiceTool>} is what
     * upstream {@code ToolService.tools(List)} consumes, which is the canonical path now that the
     * Quarkus tool dispatch loop delegates to {@code ToolService.executeInferenceAndToolsLoop(...)}.
     */
    public static List<AiServiceTool> buildAiServiceTools(Collection<Object> objectsWithTools) {
        List<AiServiceTool> result = new ArrayList<>();
        for (Object objectWithTool : objectsWithTools) {
            List<ToolMethodCreateInfo> methodCreateInfos = ToolsRecorder.fromObject(objectWithTool);
            if ((methodCreateInfos == null) || methodCreateInfos.isEmpty()) {
                continue; // this is what LangChain4j does
            }

            QuarkusToolExecutorFactory toolExecutorFactory = Arc.container().instance(QuarkusToolExecutorFactory.class).get();

            for (ToolMethodCreateInfo methodCreateInfo : methodCreateInfos) {
                String invokerClassName = methodCreateInfo.invokerClassName();
                ToolSpecification toolSpecification = methodCreateInfo.toolSpecification();
                QuarkusToolExecutor.Context executorContext = new QuarkusToolExecutor.Context(objectWithTool,
                        invokerClassName, methodCreateInfo.methodName(),
                        methodCreateInfo.argumentMapperClassName(), methodCreateInfo.executionModel(),
                        methodCreateInfo.returnBehavior(), false, methodCreateInfo);
                ToolExecutor toolExecutor = toolExecutorFactory.create(executorContext);
                result.add(AiServiceTool.builder()
                        .toolSpecification(toolSpecification)
                        .toolExecutor(toolExecutor)
                        .returnBehavior(methodCreateInfo.returnBehavior())
                        .build());
            }
        }
        return result;
    }

    /**
     * Populates the legacy {@code toolSpecifications} list and {@code toolExecutors} map from the
     * given tool-bearing objects. Used by the per-method tool override path
     * ({@code @Toolbox} / per-method {@code tools = ...}) which still keeps its own copies on
     * {@code AiServiceMethodCreateInfo}. The same scan also threads {@link ReturnBehavior} into the
     * supplied map so the caller can hand it to {@code ToolServiceContext.builder().returnBehaviors(...)}.
     */
    public static void populateToolMetadata(Collection<Object> objectsWithTools, List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors, Map<String, ReturnBehavior> returnBehaviors) {
        for (AiServiceTool tool : buildAiServiceTools(objectsWithTools)) {
            toolSpecifications.add(tool.toolSpecification());
            toolExecutors.put(tool.name(), tool.toolExecutor());
            if (returnBehaviors != null) {
                returnBehaviors.put(tool.name(), tool.returnBehavior());
            }
        }
    }

    private static List<ToolMethodCreateInfo> fromObject(Object obj) {
        // Fast path first.
        String className = obj.getClass().getName();
        var fast = metadata.get(className);
        if (fast != null) {
            return fast;
        }

        try {
            // needed for subclass check
            for (Map.Entry<String, List<ToolMethodCreateInfo>> entry : metadata.entrySet()) {
                String targetClassName = entry.getKey();
                var targetClass = Class.forName(targetClassName, false, Thread.currentThread().getContextClassLoader());
                if (targetClass.isAssignableFrom(obj.getClass())) {
                    metadata.put(targetClassName, entry.getValue()); // For the next lookup.
                    return entry.getValue();
                }
            }
            return Collections.emptyList();
        } catch (ClassNotFoundException e) {
            log.error(e);
            return Collections.emptyList();
        }
    }
}
