package io.quarkiverse.langchain4j.deployment.items;

import io.quarkiverse.langchain4j.prompt.PromptTemplateUsage;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * Produced for every AI service method message that is loaded from a prompt template registry via
 * {@link io.quarkiverse.langchain4j.SystemMessageFromRegistry} or
 * {@link io.quarkiverse.langchain4j.UserMessageFromRegistry}.
 * <p>
 * Registry extensions can consume these build items to validate the referenced templates eagerly.
 */
public final class PromptTemplateUsageBuildItem extends MultiBuildItem {

    private final PromptTemplateUsage usage;

    public PromptTemplateUsageBuildItem(PromptTemplateUsage usage) {
        this.usage = usage;
    }

    public PromptTemplateUsage getUsage() {
        return usage;
    }
}
