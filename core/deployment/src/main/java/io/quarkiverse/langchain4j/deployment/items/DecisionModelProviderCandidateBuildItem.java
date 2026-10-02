package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class DecisionModelProviderCandidateBuildItem extends MultiBuildItem implements ProviderHolder {

    private final String provider;

    public DecisionModelProviderCandidateBuildItem(String provider) {
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
