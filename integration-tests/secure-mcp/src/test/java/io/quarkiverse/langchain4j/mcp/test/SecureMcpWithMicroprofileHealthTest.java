package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.startServerHttp;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.smallrye.jwt.build.Jwt;
import io.vertx.core.json.JsonObject;

class SecureMcpWithMicroprofileHealthTest {

    private static Process process;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class)
                    .addAsResource("privateKey.pem"))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.url", "http://localhost:8082/mcp/sse")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.microprofile-health-check", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.tool-execution-timeout", "5s");

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("mcp_server_microprofile_health.java");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    @Test
    void testMicroprofileHealth() throws Exception {
        Response healthReadyResponse = RestAssured.when().get("http://localhost:8081/q/health/ready");
        JsonObject jsonHealth = new JsonObject(healthReadyResponse.asString());
        JsonObject mcpCheck = jsonHealth.getJsonArray("checks").getJsonObject(0);
        assertEquals("UP", mcpCheck.getString("status"));
        assertEquals("MCP clients health check", mcpCheck.getString("name"));

        JsonObject data = mcpCheck.getJsonObject("data");
        assertEquals("OK", data.getString("client1"));
    }

    @Test
    void testAuthenticationSuccessful() throws Exception {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("getUserName")
                .build();
        Assertions.assertThat(mcpClient.executeTool(toolExecutionRequest))
                .isEqualTo(ToolExecutionResult.builder().resultText("alice").isError(false).build());
    }

    @Test
    void testAuthenticationFailedWithRestAssured() throws Exception {
        RestAssured.when().get("http://localhost:8082/mcp/sse")
                .then()
                .statusCode(401);
    }

    @ApplicationScoped
    public static class DummyMcpClientAuthProvider implements McpClientAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer " + getJwt();
        }
    }

    static String getJwt() {
        return Jwt.preferredUserName("alice").sign("privateKey.pem");
    }
}
