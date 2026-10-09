package io.quarkiverse.langchain4j.chatscopes.deployment;

import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.chatscopes.websocket.internal.ChatRouteEndpoint;
import io.quarkiverse.langchain4j.chatscopes.websocket.internal.MarkdownToHtmlInterceptor;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.vertx.http.deployment.spi.WebDependencyJarBuildItem;
import io.quarkus.websockets.next.runtime.ConnectionManager;
import io.quarkus.websockets.next.runtime.WebSocketHttpServerOptionsCustomizer;

public class ChatScopeWebsocketProcessor {

    private static final String RUNTIME_GROUP_ID = "io.quarkiverse.langchain4j";
    private static final String RUNTIME_ARTIFACT_ID = "quarkus-langchain4j-chat-scopes-websocket";
    private static final String JS_CLIENT_SPECIFIER = "@quarkiverse/chat-scopes";
    private static final String JS_CLIENT_PATH = "/_chat/javascript/chatscopes.js";

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

    /**
     * Advertise the javascript client to UI extensions (web-dependency-locator, web-bundler, ...) so that
     * applications can {@code import { ChatScopesClient } from '@quarkiverse/chat-scopes'} instead of hardcoding
     * the URL it is served on. The module is served by this extension, so only the mapping is published.
     */
    @BuildStep
    void registerWebDependency(List<ChatRouteBuildItem> routes,
            CurateOutcomeBuildItem curateOutcome,
            BuildProducer<WebDependencyJarBuildItem> producer) {
        if (routes.isEmpty()) {
            return;
        }
        curateOutcome.getApplicationModel().getDependencies().stream()
                .filter(dep -> dep.getGroupId().equals(RUNTIME_GROUP_ID)
                        && dep.getArtifactId().equals(RUNTIME_ARTIFACT_ID))
                .findFirst()
                .ifPresent(dep -> producer.produce(new WebDependencyJarBuildItem(
                        dep.getKey(),
                        dep.getResolvedPaths().getSinglePath(),
                        Map.of(JS_CLIENT_SPECIFIER, JS_CLIENT_PATH))));
    }

}
