package io.quarkiverse.langchain4j.agentic.deployment;

import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.MethodInfo;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * TODO: add
 */
public final class DetectedAgentBuildItem extends MultiBuildItem {

    private final ClassInfo iface;
    private final List<MethodInfo> agenticMethods;

    public DetectedAgentBuildItem(ClassInfo iface, List<MethodInfo> agenticMethods) {
        this.iface = iface;
        this.agenticMethods = agenticMethods;
    }

    public ClassInfo getIface() {
        return iface;
    }

    public List<MethodInfo> getAgenticMethods() {
        return agenticMethods;
    }
}
