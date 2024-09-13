package io.quarkiverse.langchain4j.deployment.items;

import java.util.function.Predicate;

import org.jboss.jandex.AnnotationInstance;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * All Ai service method parameters with annotations matching {@code predicate} are forcedly allowed to be part of the prompt
 * template.
 */
public final class MethodParameterAllowedAnnotationsBuildItem extends MultiBuildItem {

    private final Predicate<AnnotationInstance> predicate;

    public MethodParameterAllowedAnnotationsBuildItem(Predicate<AnnotationInstance> predicate) {
        this.predicate = predicate;
    }

    public Predicate<AnnotationInstance> getPredicate() {
        return predicate;
    }
}
