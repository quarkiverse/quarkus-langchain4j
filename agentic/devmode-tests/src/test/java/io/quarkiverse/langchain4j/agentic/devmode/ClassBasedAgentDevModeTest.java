package io.quarkiverse.langchain4j.agentic.devmode;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.ServerSocket;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;

/**
 * Verifies that a class-based agent, whose native agentic method is implemented at build time, follows the changes
 * of its own source and of the beans it injects in dev mode.
 */
public class ClassBasedAgentDevModeTest {

    static final int HTTP_PORT = findAvailablePort();

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(LoopBeanResource.class, LoopThreshold.class, CountingLoopBean.class,
                                    CountingLoopAgent.class, CountingLoopAgent.Counter.class)
                            .addAsResource(new StringAsset(String.join("\n",
                                    "quarkus.http.port=" + HTTP_PORT,
                                    "quarkus.wiremock.devservices.reload=false",
                                    "quarkus.langchain4j.openai.api-key=whatever",
                                    "quarkus.langchain4j.openai.base-url=http://localhost:${quarkus.wiremock.devservices.port}/v1")),
                                    "application.properties"));

    @Test
    void classBasedAgentFollowsReloads() {
        assertEquals("3", get("/loop-bean"));

        devModeTest.modifySourceFile(LoopThreshold.class, source -> source.replace("return 3;", "return 5;"));
        assertEquals("5", get("/loop-bean"));

        devModeTest.modifySourceFile(CountingLoopBean.class,
                source -> source.replace("count >= threshold.value()", "count > threshold.value()"));
        assertEquals("6", get("/loop-bean"));
    }

    private static String get(String path) {
        return given()
                .port(HTTP_PORT)
                .get(path)
                .then()
                .statusCode(200)
                .extract()
                .asString();
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to find an available HTTP port for dev mode test", e);
        }
    }
}
