package io.quarkiverse.langchain4j.deployment;

import java.util.function.Predicate;

import org.jboss.jandex.MethodInfo;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item which indicated when an AI Service method should not have output instructions associated with it
 */
public final class SkipOutputFormatInstructionsBuildItem extends MultiBuildItem {

    private final Predicate<MethodInfo> predicate;

    public SkipOutputFormatInstructionsBuildItem(Predicate<MethodInfo> predicate) {
        this.predicate = predicate;
    }

    public Predicate<MethodInfo> getPredicate() {
        return predicate;
    }
}
