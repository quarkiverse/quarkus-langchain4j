package io.quarkiverse.langchain4j.mcp.test.integration;

import static io.quarkiverse.langchain4j.mcp.test.integration.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.integration.McpServerHelper.startServerHttp;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class McpClientStreamableHttpTest {

    private static Process process;

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("simple_mcp_server.java");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    @Test
    public void callToolOverStreamableHttp() {
        RestAssured.given()
                .get("/streamable-http")
                .then()
                .assertThat()
                .body(Matchers.equalTo("OK"));
    }

}
