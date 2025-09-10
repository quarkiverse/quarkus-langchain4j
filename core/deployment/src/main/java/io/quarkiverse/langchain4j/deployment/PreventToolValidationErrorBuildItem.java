package io.quarkiverse.langchain4j.deployment;

import java.util.function.Predicate;

import org.jboss.jandex.ClassInfo;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item that prevents the default validation exception from being thrown for invalid AiService methods
 */
public final class PreventToolValidationErrorBuildItem extends MultiBuildItem {

    private final Predicate<ClassInfo> predicate;

    public PreventToolValidationErrorBuildItem(Predicate<ClassInfo> predicate) {
        this.predicate = predicate;
    }

    public Predicate<ClassInfo> getPredicate() {
        return predicate;
    }
}
