package io.quarkiverse.langchain4j.agentic.deployment;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final String modelName;
    private final List<MethodInfo> mcpToolBoxMethods;
    private final List<MethodInfo> toolBoxMethods;
    private final List<MethodInfo> skillsMethods;

    public DetectedAiAgentBuildItem(ClassInfo iface, List<MethodInfo> agenticMethods,
            MethodInfo chatModelSupplier, String modelName, List<MethodInfo> mcpToolBoxMethods,
            List<MethodInfo> toolBoxMethods, List<MethodInfo> skillsMethods) {
        this.iface = iface;
        this.agenticMethods = agenticMethods;
        this.chatModelSupplier = chatModelSupplier;
        this.modelName = modelName;
        this.mcpToolBoxMethods = mcpToolBoxMethods;
        this.toolBoxMethods = toolBoxMethods;
        this.skillsMethods = skillsMethods;
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

    public String getModelName() {
        return modelName;
    }

    public List<MethodInfo> getMcpToolBoxMethods() {
        return mcpToolBoxMethods;
    }

    public List<MethodInfo> getToolBoxMethods() {
        return toolBoxMethods;
    }

    public List<MethodInfo> getSkillsMethods() {
        return skillsMethods;
    }

    public static Set<ClassInfo> allIfaces(Collection<DetectedAiAgentBuildItem> items) {
        return items.stream().map(DetectedAiAgentBuildItem::getIface).collect(Collectors.toSet());
    }
}
