package io.quarkiverse.langchain4j.deployment.items;

import org.jboss.jandex.MethodInfo;

import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.builder.item.MultiBuildItem;

public final class ToolMethodBuildItem extends MultiBuildItem {

    private final MethodInfo toolsMethodInfo;

    private final ToolMethodCreateInfo toolMethodCreateInfo;

    public ToolMethodBuildItem(MethodInfo toolsMethodInfo, ToolMethodCreateInfo toolMethodCreateInfo) {
        this.toolsMethodInfo = toolsMethodInfo;
        this.toolMethodCreateInfo = toolMethodCreateInfo;
    }

    public MethodInfo getToolsMethodInfo() {
        return toolsMethodInfo;
    }

    public String getDeclaringClassName() {
        return toolsMethodInfo.declaringClass().name().toString();
    }

    public ToolMethodCreateInfo getToolMethodCreateInfo() {
        return toolMethodCreateInfo;
    }

    /**
     * Returns true if the method requires a switch to a worker thread, even if the method is non-blocking.
     * This is because of the tools executor limitation (imperative API).
     *
     *
     * @return true if the method requires a switch to a worker thread
     */
    public boolean requiresSwitchToWorkerThread() {
        return !(toolMethodCreateInfo.executionModel() == ToolMethodCreateInfo.ExecutionModel.NON_BLOCKING
                && isImperativeMethod());

    }

    private boolean isImperativeMethod() {
        var type = toolsMethodInfo.returnType();
        return !DotNames.UNI.equals(type.name())
                && !DotNames.MULTI.equals(type.name())
                && !DotNames.COMPLETION_STAGE.equals(type.name());
    }
}
