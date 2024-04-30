package io.quarkiverse.langchain4j.websockets.next.deployment;

import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;
import io.quarkiverse.langchain4j.websockets.next.runtime.WebSocketConnectionDefaultMemoryIdProvider;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;

public class WebSocketsNextProcessor {

    private static final String FEATURE = "langchain4j-websockets-next";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ServiceProviderBuildItem serviceProvider() {
        return new ServiceProviderBuildItem(DefaultMemoryIdProvider.class.getName(),
                WebSocketConnectionDefaultMemoryIdProvider.class.getName());
    }
}
