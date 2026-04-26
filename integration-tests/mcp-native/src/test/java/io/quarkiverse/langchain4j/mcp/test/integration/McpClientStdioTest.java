package io.quarkiverse.langchain4j.mcp.test.integration;

import static io.quarkiverse.langchain4j.mcp.test.integration.McpServerHelper.copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready;
import static io.quarkiverse.langchain4j.mcp.test.integration.McpServerHelper.skipTestsIfJbangNotAvailable;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class McpClientStdioTest {

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready("simple_mcp_server.java");
    }

    @Test
    public void callToolOverStdio() {
        RestAssured.given()
                .get("/stdio")
                .then()
                .assertThat()
                .body(Matchers.equalTo("OK"));
    }

}
