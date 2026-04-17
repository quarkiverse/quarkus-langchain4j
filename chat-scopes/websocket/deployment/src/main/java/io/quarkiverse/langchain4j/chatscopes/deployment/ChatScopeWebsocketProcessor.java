package io.quarkiverse.langchain4j.chatscopes.deployment;

import java.util.List;

import io.quarkiverse.langchain4j.chatscopes.websocket.internal.ChatRouteEndpoint;
import io.quarkiverse.langchain4j.chatscopes.websocket.internal.MarkdownToHtmlInterceptor;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.websockets.next.runtime.ConnectionManager;
import io.quarkus.websockets.next.runtime.WebSocketHttpServerOptionsCustomizer;

public class ChatScopeWebsocketProcessor {
    @BuildStep
    void registerBeans(List<ChatRouteBuildItem> routes, BuildProducer<AdditionalBeanBuildItem> producer) {
        if (routes.isEmpty()) {
            return;
        }
        producer.produce(
                AdditionalBeanBuildItem.builder()
                        .addBeanClasses(ChatRouteEndpoint.class, ConnectionManager.class,
                                WebSocketHttpServerOptionsCustomizer.class, MarkdownToHtmlInterceptor.class)
                        .setUnremovable()
                        .build());
    }

}
