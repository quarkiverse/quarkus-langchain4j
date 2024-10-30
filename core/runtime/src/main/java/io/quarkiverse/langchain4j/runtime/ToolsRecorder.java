package io.quarkiverse.langchain4j.runtime;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolSpecification;
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

    public static void populateToolMetadata(List<Object> objectsWithTools, List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors) {
        for (Object objectWithTool : objectsWithTools) {
            List<ToolMethodCreateInfo> methodCreateInfos = ToolsRecorder.fromObject(objectWithTool);
            if ((methodCreateInfos == null) || methodCreateInfos.isEmpty()) {
                continue; // this is what LangChain4j does
            }

            QuarkusToolExecutorFactory toolExecutorFactory = Arc.container().instance(QuarkusToolExecutorFactory.class).get();

            for (ToolMethodCreateInfo methodCreateInfo : methodCreateInfos) {
                String invokerClassName = methodCreateInfo.invokerClassName();
                ToolSpecification toolSpecification = methodCreateInfo.toolSpecification();
                toolSpecifications.add(toolSpecification);
                QuarkusToolExecutor.Context executorContext = new QuarkusToolExecutor.Context(objectWithTool,
                        invokerClassName, methodCreateInfo.methodName(),
                        methodCreateInfo.argumentMapperClassName(), methodCreateInfo.executionModel());
                toolExecutors.put(toolSpecification.name(), toolExecutorFactory.create(executorContext));
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
