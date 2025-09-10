package io.quarkiverse.langchain4j.deployment;

import java.util.List;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item that can be used in order to make an interface an AiService automatically
 */
public final class AnnotationsImpliesAiServiceBuildItem extends MultiBuildItem {

    private final List<DotName> annotationNames;

    public AnnotationsImpliesAiServiceBuildItem(List<DotName> annotationNames) {
        this.annotationNames = annotationNames;
    }

    public List<DotName> getAnnotationNames() {
        return annotationNames;
    }
}
