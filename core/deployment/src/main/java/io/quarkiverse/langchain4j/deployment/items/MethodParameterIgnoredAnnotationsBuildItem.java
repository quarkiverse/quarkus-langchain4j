package io.quarkiverse.langchain4j.deployment.items;

import java.util.function.Predicate;

import org.jboss.jandex.AnnotationInstance;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * All Ai service method parameters with annotations matching {@code predicate} are ignored when determining if a method
 * parameter is to be included as template variable in the prompt template.
 */
public final class MethodParameterIgnoredAnnotationsBuildItem extends MultiBuildItem {

    private final Predicate<AnnotationInstance> predicate;

    public MethodParameterIgnoredAnnotationsBuildItem(Predicate<AnnotationInstance> predicate) {
        this.predicate = predicate;
    }

    public Predicate<AnnotationInstance> getPredicate() {
        return predicate;
    }
}
