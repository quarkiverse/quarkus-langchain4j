package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Contains information about what provider has been configured for a certain model based on user configuration.
 * This only emitted when the configuration is runtime fixed and can be conclusively determined at build time.
 * The usefulness of this is to aid the provider resolution process.
 */
public final class ImplicitlyUserConfiguredChatProviderBuildItem extends MultiBuildItem {

    private final String configName;
    private final String provider;

    public ImplicitlyUserConfiguredChatProviderBuildItem(String configName, String provider) {
        this.configName = configName;
        this.provider = provider;
    }

    public String getConfigName() {
        return configName;
    }

    public String getProvider() {
        return provider;
    }
}
