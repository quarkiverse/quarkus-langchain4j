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
 * The javascript client is published to UI extensions through the web dependency SPI. With
 * quarkus-web-dependency-locator on the classpath that means it ends up in the generated import map.
 */
public class WebDependencyTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(MyChatService.class));

    @ApplicationScoped
    public static class MyChatService {

        @ChatRoute("test")
        public String chat(@UserMessage String userMessage) {
            return "Received: " + userMessage;
        }
    }

    @Test
    public void testClientIsAnEsModule() throws IOException, InterruptedException {
        String js = get("/_chat/javascript/chatscopes.js");
        Assertions.assertTrue(js.contains("export class ChatScopesClient"),
                "The javascript client should be an ES module so that it can be imported: " + js);
    }

    @Test
    public void testClientIsInTheImportMap() throws IOException, InterruptedException {
        String importMap = get("/_importmap/generated_importmap.js");
        Assertions.assertTrue(importMap.contains("@quarkiverse/chat-scopes"),
                "The import map should contain the chat scopes specifier: " + importMap);
        Assertions.assertTrue(importMap.contains("/_chat/javascript/chatscopes.js"),
                "The import map should map to the served javascript client: " + importMap);
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
