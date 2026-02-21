package io.quarkiverse.langchain4j.agentic.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import io.quarkus.builder.item.SimpleBuildItem;

public final class DetectedNonAiAgentAsMapBuildItem extends SimpleBuildItem {

    private final Map<DotName, List<MethodInfo>> classToNonAiAgentMethodsMap;

    public DetectedNonAiAgentAsMapBuildItem(Map<DotName, List<MethodInfo>> classToNonAiAgentMethodsMap) {
        this.classToNonAiAgentMethodsMap = classToNonAiAgentMethodsMap;
    }

    public static DetectedNonAiAgentAsMapBuildItem from(Map<ClassInfo, List<MethodInfo>> map) {
        Map<DotName, List<MethodInfo>> buildItemMap = new HashMap<>();
        for (Map.Entry<ClassInfo, List<MethodInfo>> entry : map.entrySet()) {
            buildItemMap.put(entry.getKey().name(), entry.getValue());
        }
        return new DetectedNonAiAgentAsMapBuildItem(buildItemMap);
    }

    public Map<DotName, List<MethodInfo>> getClassToNonAiAgentMethodsMap() {
        return classToNonAiAgentMethodsMap;
    }
}
