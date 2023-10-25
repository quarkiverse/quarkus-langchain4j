package io.quarkiverse.langchain4j.runtime;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceClassCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class AiServicesRecorder {

    // the key is the interface's class name
    private static final Map<String, AiServiceClassCreateInfo> metadata = new HashMap<>();

    public void setMetadata(Map<String, AiServiceClassCreateInfo> metadata) {
        AiServicesRecorder.metadata.putAll(metadata);
    }

    public static Map<String, AiServiceClassCreateInfo> getMetadata() {
        return metadata;
    }

    public static void clearMetadata() {
        metadata.clear();
    }

    @SuppressWarnings("unused") // used in generated code
    public static AiServiceMethodCreateInfo getAiServiceMethodCreateInfo(String className, String methodId) {
        AiServiceClassCreateInfo classCreateInfo = metadata.get(className);
        if (classCreateInfo == null) {
            throw new RuntimeException("Quarkus was not able to determine class '" + className
                    + "' as an AiService at build time. Consider annotating the clas with @CreatedAware");
        }
        AiServiceMethodCreateInfo methodCreateInfo = classCreateInfo.getMethodMap().get(methodId);
        if (methodCreateInfo == null) {
            throw new IllegalStateException("Unable to locate method metadata for descriptor '" + methodId
                    + "'. Please report this issue to the maintainers");
        }
        return methodCreateInfo;
    }
}
