package io.quarkiverse.langchain4j.deployment;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Allows extension to request the creation of a {@link dev.langchain4j.model.chat.ChatLanguageModel} bean
 * even if no injection point exists.
 */
public final class RequestChatModelBeanBuildItem extends MultiBuildItem {
}
