package io.quarkiverse.langchain4j.deployment;

import java.util.function.Predicate;

import org.jboss.jandex.MethodInfo;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item which indicates that a dummy user message should be created if otherwise the processing would fail
 */
public final class FallbackToDummyUserMessageBuildItem extends MultiBuildItem {

    private final Predicate<MethodInfo> predicate;

    public FallbackToDummyUserMessageBuildItem(Predicate<MethodInfo> predicate) {
        this.predicate = predicate;
    }

    public Predicate<MethodInfo> getPredicate() {
        return predicate;
    }
}
