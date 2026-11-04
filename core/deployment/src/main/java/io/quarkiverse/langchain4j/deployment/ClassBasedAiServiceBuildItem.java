package io.quarkiverse.langchain4j.deployment;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Declares a class, rather than an interface, as an AI service implied by its annotations: its {@code native} and
 * abstract methods are implemented by a generated subclass that delegates to the AI service machinery, like the
 * methods of an AI service interface are, while the class itself remains the bean. Produced by the agentic extension
 * for the leaf AI agents declared as classes.
 */
public final class ClassBasedAiServiceBuildItem extends MultiBuildItem {

    private final DotName className;

    public ClassBasedAiServiceBuildItem(DotName className) {
        this.className = className;
    }

    public DotName getClassName() {
        return className;
    }
}
