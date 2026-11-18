package io.quarkiverse.langchain4j.deployment;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Makes an interface auto-registered through the implied registration mechanism use no chat memory,
 * instead of the default {@code ChatMemoryProvider} bean.
 *
 * <p>
 * Other Quarkus extensions can produce this build item for interfaces whose memory is configured
 * elsewhere, or that must be stateless by default. It has no effect on interfaces explicitly
 * registered via {@code @RegisterAiService}.
 */
public final class ImpliedAiServiceWithoutChatMemoryBuildItem extends MultiBuildItem {

    private final DotName className;

    public ImpliedAiServiceWithoutChatMemoryBuildItem(DotName className) {
        this.className = className;
    }

    public ImpliedAiServiceWithoutChatMemoryBuildItem(String className) {
        this(DotName.createSimple(className));
    }

    public DotName getClassName() {
        return className;
    }
}
