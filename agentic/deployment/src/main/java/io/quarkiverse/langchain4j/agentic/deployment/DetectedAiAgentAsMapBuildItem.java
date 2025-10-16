package io.quarkiverse.langchain4j.agentic.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import io.quarkus.builder.item.SimpleBuildItem;

public final class DetectedAiAgentAsMapBuildItem extends SimpleBuildItem {

    private final Map<DotName, List<MethodInfo>> ifaceToAgentMethodsMap;

    public DetectedAiAgentAsMapBuildItem(Map<DotName, List<MethodInfo>> ifaceToAgentMethodsMap) {
        this.ifaceToAgentMethodsMap = ifaceToAgentMethodsMap;
    }

    public static DetectedAiAgentAsMapBuildItem from(Map<ClassInfo, List<MethodInfo>> map) {
        Map<DotName, List<MethodInfo>> buildItemMap = new HashMap<>();
        for (Map.Entry<ClassInfo, List<MethodInfo>> entry : map.entrySet()) {
            buildItemMap.put(entry.getKey().name(), entry.getValue());
        }
        return new DetectedAiAgentAsMapBuildItem(buildItemMap);
    }

    public Map<DotName, List<MethodInfo>> getIfaceToAgentMethodsMap() {
        return ifaceToAgentMethodsMap;
    }
}
