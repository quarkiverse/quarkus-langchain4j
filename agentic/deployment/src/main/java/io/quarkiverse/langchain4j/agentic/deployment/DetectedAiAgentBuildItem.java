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

    public DetectedAiAgentBuildItem(ClassInfo iface, List<MethodInfo> agenticMethods,
            MethodInfo chatModelSupplier) {
        this.iface = iface;
        this.agenticMethods = agenticMethods;
        this.chatModelSupplier = chatModelSupplier;
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
}
