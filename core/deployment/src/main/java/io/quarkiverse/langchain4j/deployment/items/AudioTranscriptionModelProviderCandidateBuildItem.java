package io.quarkiverse.langchain4j.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class AudioTranscriptionModelProviderCandidateBuildItem extends MultiBuildItem implements ProviderHolder {

    private final String provider;

    public AudioTranscriptionModelProviderCandidateBuildItem(String provider) {
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
