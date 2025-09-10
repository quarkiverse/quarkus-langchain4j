package io.quarkiverse.langchain4j.agentic.deployment;

import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.MethodInfo;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item that contains all the metadata discovered for Agents
 */
public final class DetectedAiAgentBuildItem extends MultiBuildItem {

    private final ClassInfo iface;
    private final List<MethodInfo> agenticMethods;
    private final MethodInfo chatModelSupplier;
    private final List<MethodInfo> mcpToolBoxMethods;

    public DetectedAiAgentBuildItem(ClassInfo iface, List<MethodInfo> agenticMethods,
            MethodInfo chatModelSupplier, List<MethodInfo> mcpToolBoxMethods) {
        this.iface = iface;
        this.agenticMethods = agenticMethods;
        this.chatModelSupplier = chatModelSupplier;
        this.mcpToolBoxMethods = mcpToolBoxMethods;
    }

    public ClassInfo getIface() {
        return iface;
    }

    public List<MethodInfo> getAgenticMethods() {
        return agenticMethods;
    }

    public MethodInfo getChatModelSupplier() {
        return chatModelSupplier;
    }

    public List<MethodInfo> getMcpToolBoxMethods() {
        return mcpToolBoxMethods;
    }
}
