package io.quarkiverse.langchain4j.deployment.items;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

public final class ChatModelProviderCandidateBuildItem extends MultiBuildItem implements ProviderHolder {

    private final String provider;
    private final DotName concreteType;

    public ChatModelProviderCandidateBuildItem(String provider, DotName concreteType) {
        this.provider = provider;
        this.concreteType = concreteType;
    }

    public String getProvider() {
        return provider;
    }

    public DotName getConcreteType() {
        return concreteType;
    }
}
