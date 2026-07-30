package io.quarkiverse.langchain4j.deployment;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Prevents a specific interface from being auto-registered as an AI service
 * through the implied registration mechanism (annotations like {@code @UserMessage},
 * {@code @SystemMessage}, {@code @Moderate}).
 *
 * <p>
 * Other Quarkus extensions can produce this build item to exclude their
 * library interfaces from implied registration. Interfaces excluded this way
 * can still be explicitly registered via {@code @RegisterAiService}.
 */
public final class ExcludeFromImpliedAiServiceBuildItem extends MultiBuildItem {

    private final DotName className;

    public ExcludeFromImpliedAiServiceBuildItem(DotName className) {
        this.className = className;
    }

    public ExcludeFromImpliedAiServiceBuildItem(String className) {
        this(DotName.createSimple(className));
    }

    public DotName getClassName() {
        return className;
    }
}
