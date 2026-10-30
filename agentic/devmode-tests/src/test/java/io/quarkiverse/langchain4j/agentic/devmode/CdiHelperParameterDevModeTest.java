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
 * Verifies that a {@code @CdiBean} parameter of an exit condition follows the changes of its bean in dev mode, and
 * that the CDI parameter resolver registered before a restart is replaced instead of piling up.
 */
public class CdiHelperParameterDevModeTest {

    static final int HTTP_PORT = findAvailablePort();

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(LoopResource.class, LoopThreshold.class, CountingLoopAgent.class,
                                    CountingLoopAgent.Counter.class)
                            .addAsResource(new StringAsset(String.join("\n",
                                    "quarkus.http.port=" + HTTP_PORT,
                                    "quarkus.wiremock.devservices.reload=false",
                                    "quarkus.langchain4j.openai.api-key=whatever",
                                    "quarkus.langchain4j.openai.base-url=http://localhost:${quarkus.wiremock.devservices.port}/v1")),
                                    "application.properties"));

    @Test
    void cdiBeanParameterFollowsReloads() {
        assertEquals("3", get("/loop"));

        devModeTest.modifySourceFile(LoopThreshold.class, source -> source.replace("return 3;", "return 5;"));

        assertEquals("5", get("/loop"));
    }

    @Test
    void cdiParameterResolverIsReplacedOnRestart() {
        assertEquals("1", get("/loop/resolvers"));

        devModeTest.modifySourceFile(LoopResource.class,
                source -> source.replace("public int resolvers() {", "public int resolvers() { // restart"));

        assertEquals("1", get("/loop/resolvers"));
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
