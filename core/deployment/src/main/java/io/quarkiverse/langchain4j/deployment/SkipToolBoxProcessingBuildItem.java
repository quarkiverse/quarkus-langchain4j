package io.quarkiverse.langchain4j.deployment;

import java.util.function.Predicate;

import org.jboss.jandex.MethodInfo;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item that prevents {@code @ToolBox} annotations on matching methods
 * from being processed into {@code AiServiceMethodCreateInfo.toolClassInfo}.
 * This is used when {@code @ToolBox} tools are handled at the agent builder level instead.
 */
public final class SkipToolBoxProcessingBuildItem extends MultiBuildItem {

    private final Predicate<MethodInfo> predicate;

    public SkipToolBoxProcessingBuildItem(Predicate<MethodInfo> predicate) {
        this.predicate = predicate;
    }

    public Predicate<MethodInfo> getPredicate() {
        return predicate;
    }
}
