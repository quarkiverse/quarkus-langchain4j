package io.quarkiverse.langchain4j.chatscopes.deployment;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.chatscopes.websocket.internal.ChatRouteEndpoint;
import io.quarkiverse.langchain4j.chatscopes.websocket.internal.MarkdownToHtmlInterceptor;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;
import io.quarkus.vertx.http.deployment.spi.WebDependencyJarBuildItem;
import io.quarkus.websockets.next.runtime.ConnectionManager;
import io.quarkus.websockets.next.runtime.WebSocketHttpServerOptionsCustomizer;

public class ChatScopeWebsocketProcessor {

    private static final Logger log = Logger.getLogger(ChatScopeWebsocketProcessor.class);

    private static final String RUNTIME_GROUP_ID = "io.quarkiverse.langchain4j";
    private static final String RUNTIME_ARTIFACT_ID = "quarkus-langchain4j-chat-scopes-websocket";
    private static final String JS_CLIENT_SPECIFIER = "@quarkiverse/chat-scopes";
    private static final String JS_CLIENT_PATH = "/_chat/javascript/chatscopes.js";
    private static final String JS_API_SPECIFIER = "@quarkiverse/chat-scopes-api";
    private static final String JS_API_PATH = "/_static/quarkus-chat-scopes-api/chatscopes-api.js";

    /**
     * Everything the generated module binds internally is prefixed with this, so that a route name can never
     * collide with it. Only the four public exports are left to guard, see {@link #RESERVED_EXPORTS}.
     */
    private static final String INTERNAL_PREFIX = "__cs_";

    /**
     * The exports the generated module declares itself. A route named after one of these does not get a named
     * export, it stays reachable through {@code routes["..."]}.
     */
    private static final Set<String> RESERVED_EXPORTS = Set.of("client", "endpoint", "routes", "defaultRoute");

    /**
     * Names that cannot be bound by {@code export const}. Generated modules are always strict mode, which
     * reserves more than the plain keyword list does, and forbids binding {@code arguments} and {@code eval}.
     */
    private static final Set<String> JS_RESERVED = Set.of(
            // always reserved
            "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete",
            "do", "else", "enum", "export", "extends", "false", "finally", "for", "function", "if", "import",
            "in", "instanceof", "new", "null", "return", "super", "switch", "this", "throw", "true", "try",
            "typeof", "var", "void", "while", "with",
            // reserved in strict mode, which modules always are
            "implements", "interface", "let", "package", "private", "protected", "public", "static", "yield",
            // not bindable in strict mode
            "arguments", "eval");

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
                        Map.of(JS_CLIENT_SPECIFIER, JS_CLIENT_PATH,
                                JS_API_SPECIFIER, JS_API_PATH))));
    }

    /**
     * Generate a module that knows the routes of this application, so that the browser doesn't have to spell
     * them out as strings. It builds on the hand written client in {@value #JS_CLIENT_PATH}.
     */
    @BuildStep
    void generateJsApi(List<ChatRouteBuildItem> routes,
            BuildProducer<GeneratedStaticResourceBuildItem> producer) {
        if (routes.isEmpty()) {
            return;
        }
        producer.produce(new GeneratedStaticResourceBuildItem(JS_API_PATH,
                generateJsApi(routes).getBytes(StandardCharsets.UTF_8)));
    }

    private static String generateJsApi(List<ChatRouteBuildItem> routes) {
        // sorted so that the generated module is stable across builds
        Map<String, ChatRouteBuildItem> byName = new TreeMap<>();
        for (ChatRouteBuildItem route : routes) {
            ChatRouteBuildItem clash = byName.putIfAbsent(route.getRouteName(), route);
            if (clash != null) {
                log.warnf("Chat route '%s' is declared by both %s.%s and %s.%s, only the first one is in the "
                        + "generated javascript module", route.getRouteName(), clash.getClassName(),
                        clash.getMethodName(), route.getClassName(), route.getMethodName());
            }
        }

        StringBuilder js = new StringBuilder();
        js.append("// Generated by quarkus-langchain4j-chat-scopes-websocket. Do not edit.\n");
        js.append("import { ChatScopesClient as __cs_Client, chatRoutesEndpoint as __cs_endpoint } from '")
                .append(JS_CLIENT_SPECIFIER).append("';\n\n");
        js.append("export const endpoint = __cs_endpoint();\n");
        js.append("export const client = new __cs_Client();\n\n");
        // everything below binds under INTERNAL_PREFIX, so no route name can shadow it
        js.append("""
                let __cs_opening = null;
                function __cs_open() {
                    if (__cs_opening === null) {
                        __cs_opening = client.open(endpoint);
                    }
                    return __cs_opening;
                }

                async function __cs_connect(name, handlers) {
                    await __cs_open();
                    const builder = client.builder();
                    const { eventHandlers, ...rest } = handlers || {};
                    for (const [type, handler] of Object.entries(eventHandlers || {})) {
                        builder.eventHandler(type, handler);
                    }
                    for (const [key, handler] of Object.entries(rest)) {
                        if (key === 'eventHandler') {
                            throw new Error("use eventHandlers: { <type>: handler } for custom event types");
                        }
                        if (typeof builder[key] !== 'function') {
                            throw new Error("'" + key + "' is not a chat scopes handler");
                        }
                        builder[key](handler);
                    }
                    return builder.connect(name);
                }

                function __cs_route(name, streaming) {
                    return {
                        name: name,
                        streaming: streaming,
                        connect: (handlers) => __cs_connect(name, handlers)
                    };
                }

                """);

        js.append("export const routes = {\n");
        int i = 0;
        for (Map.Entry<String, ChatRouteBuildItem> entry : byName.entrySet()) {
            String name = entry.getKey();
            js.append("    '").append(escapeJsString(name)).append("': __cs_route('").append(escapeJsString(name))
                    .append("', ").append(entry.getValue().isStreaming()).append(")");
            js.append(++i < byName.size() ? ",\n" : "\n");
        }
        js.append("};\n\n");

        // a lone route is the default one even without @DefaultChatRoute, mirroring what
        // ChatScopeProcessor.registerChatRoutes does on the server
        String defaultRoute = routes.size() == 1 ? routes.get(0).getRouteName()
                : byName.values().stream()
                        .filter(ChatRouteBuildItem::isDefaultRoute)
                        .map(ChatRouteBuildItem::getRouteName)
                        .findFirst()
                        .orElse(null);
        js.append("export const defaultRoute = ")
                .append(defaultRoute == null ? "null" : "routes['" + escapeJsString(defaultRoute) + "']")
                .append(";\n");

        // A route is only given a named export when its name happens to be usable as one. Routes without an
        // explicit @ChatRoute value are named "<class>::<method>", which never is.
        StringBuilder named = new StringBuilder();
        for (String name : byName.keySet()) {
            if (isExportableName(name)) {
                named.append("export const ").append(name).append(" = routes['").append(name).append("'];\n");
            }
        }
        if (named.length() > 0) {
            js.append("\n").append(named);
        }
        return js.toString();
    }

    private static boolean isExportableName(String name) {
        if (name.isEmpty() || RESERVED_EXPORTS.contains(name) || JS_RESERVED.contains(name)
                || name.startsWith(INTERNAL_PREFIX)) {
            return false;
        }
        if (Character.isDigit(name.charAt(0))) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ascii = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (!ascii && c != '_' && c != '$') {
                return false;
            }
        }
        return true;
    }

    private static String escapeJsString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

}
