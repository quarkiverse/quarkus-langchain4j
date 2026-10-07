package io.quarkiverse.langchain4j.chatscopes.tests;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.chatscopes.ChatRoute;
import io.quarkiverse.langchain4j.chatscopes.DefaultChatRoute;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

/**
 * The routes of the application are generated into a javascript module so the browser does not have to
 * spell them out as strings.
 */
public class GeneratedJsApiTest {

    private static final String API = "/_static/quarkus-chat-scopes-api/chatscopes-api.js";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyChatService.class));

    @ApplicationScoped
    public static class MyChatService {

        @ChatRoute("travel")
        @DefaultChatRoute
        public String travel(@UserMessage String userMessage) {
            return "Received: " + userMessage;
        }

        @ChatRoute("ticker")
        public Multi<String> ticker() {
            return Multi.createFrom().items("1", "2");
        }

        // no explicit name, so the route is called "<class>::<method>" which is not a valid export name
        @ChatRoute
        public String unnamed(@UserMessage String userMessage) {
            return "Received: " + userMessage;
        }

        // collides with a binding the generated module needs for itself
        @ChatRoute("connect")
        public String connect(@UserMessage String userMessage) {
            return "Received: " + userMessage;
        }

        // reserved in strict mode, which an ES module always is
        @ChatRoute("public")
        public String publicRoute(@UserMessage String userMessage) {
            return "Received: " + userMessage;
        }

        // shaped like one of the module's own bindings
        @ChatRoute("__cs_route")
        public String internalLookalike(@UserMessage String userMessage) {
            return "Received: " + userMessage;
        }
    }

    @Test
    public void testRoutesAreGenerated() throws IOException, InterruptedException {
        String js = get(API);
        Assertions.assertTrue(js.contains("'travel': __cs_route('travel', false)"), js);
        Assertions.assertTrue(js.contains("'ticker': __cs_route('ticker', true)"),
                "A route returning Multi should be marked as streaming: " + js);
    }

    @Test
    public void testCustomEventHandlersAreSupported() throws IOException, InterruptedException {
        String js = get(API);
        Assertions.assertTrue(js.contains("builder.eventHandler(type, handler)"),
                "connect() should accept custom event types through eventHandlers: " + js);
    }

    @Test
    public void testDefaultRoute() throws IOException, InterruptedException {
        Assertions.assertTrue(get(API).contains("export const defaultRoute = routes['travel'];"), get(API));
    }

    @Test
    public void testNamedExportsOnlyForUsableNames() throws IOException, InterruptedException {
        String js = get(API);
        Assertions.assertTrue(js.contains("export const travel = routes['travel'];"), js);
        Assertions.assertTrue(js.contains("::unnamed'"),
                "An unnamed route should still be reachable through routes[...]: " + js);
        Assertions.assertFalse(js.contains("export const " + MyChatService.class.getName()),
                "A route name that is not a valid identifier should not become a named export: " + js);
    }

    /**
     * A name that strict mode reserves, or one of the module's own bindings, would make the whole module
     * fail to parse rather than just that route. Such routes stay reachable through routes[...].
     */
    @Test
    public void testNamesThatWouldBreakTheModuleAreNotExported() throws IOException, InterruptedException {
        String js = get(API);
        for (String name : new String[] { "public", "__cs_route" }) {
            Assertions.assertTrue(js.contains("'" + name + "': __cs_route('" + name + "'"),
                    "Route '" + name + "' should still be in routes[...]: " + js);
            Assertions.assertFalse(js.contains("export const " + name + " ="),
                    "Route '" + name + "' must not become a named export: " + js);
        }
    }

    /**
     * Everything the module needs for itself is prefixed, so names like this are free for routes to use.
     */
    @Test
    public void testModuleInternalsDoNotStealNames() throws IOException, InterruptedException {
        String js = get(API);
        Assertions.assertTrue(js.contains("export const connect = routes['connect'];"), js);
    }

    @Test
    public void testApiIsInTheImportMap() throws IOException, InterruptedException {
        String importMap = get("/_importmap/generated_importmap.js");
        Assertions.assertTrue(importMap.contains("@quarkiverse/chat-scopes-api"), importMap);
        Assertions.assertTrue(importMap.contains(API), importMap);
    }

    private static String get(String path) throws IOException, InterruptedException {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, response.statusCode(), "Unexpected status for " + path);
        return response.body();
    }
}
