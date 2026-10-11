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
import io.quarkus.test.QuarkusUnitTest;

/**
 * The server treats a lone route as the default one even without {@code @DefaultChatRoute}, so the
 * generated module has to agree with it.
 */
public class SingleRouteJsApiTest {

    private static final String API = "/_static/quarkus-chat-scopes-api/chatscopes-api.js";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(OnlyChatService.class));

    @ApplicationScoped
    public static class OnlyChatService {

        @ChatRoute("solo")
        public String chat(@UserMessage String userMessage) {
            return "Received: " + userMessage;
        }
    }

    @Test
    public void testLoneRouteIsTheDefault() throws IOException, InterruptedException {
        String js = get(API);
        Assertions.assertTrue(js.contains("export const defaultRoute = routes['solo'];"),
                "A lone route is the default on the server, so it should be here too: " + js);
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
