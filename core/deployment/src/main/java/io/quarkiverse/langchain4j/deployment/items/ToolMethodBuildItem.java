package io.quarkiverse.langchain4j.deployment.items;

import org.jboss.jandex.MethodInfo;

import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item that represents a method that is annotated with {@link dev.langchain4j.agent.tool.Tool}.
 * It contains the method info and the tool method create info.
 */
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
     * Returns true if the method requires a switch to a worker thread when tools are executed asynchronously.
     * Non-blocking tools, including those returning {@code Uni} or {@code CompletionStage}, do not; tools returning
     * {@code Multi} do.
     *
     * @return true if the method requires a switch to a worker thread
     */
    public boolean requiresSwitchToWorkerThread() {
        return toolMethodCreateInfo.executionModel() != ToolMethodCreateInfo.ExecutionModel.NON_BLOCKING
                || DotNames.MULTI.equals(toolsMethodInfo.returnType().name());
    }

    /**
     * Returns true if the method requires a switch to a worker thread when tools are executed synchronously, as the
     * upstream {@code TokenStream} does: only non-blocking tools with an imperative return type can run on the event
     * loop.
     *
     * @return true if the method requires a switch to a worker thread
     */
    public boolean requiresSwitchToWorkerThreadForSynchronousExecution() {
        var type = toolsMethodInfo.returnType();
        return toolMethodCreateInfo.executionModel() != ToolMethodCreateInfo.ExecutionModel.NON_BLOCKING
                || DotNames.UNI.equals(type.name())
                || DotNames.MULTI.equals(type.name())
                || DotNames.COMPLETION_STAGE.equals(type.name());
    }
}
