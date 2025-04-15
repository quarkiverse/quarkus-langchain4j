package io.quarkiverse.langchain4j.mcp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.mcp.runtime.McpClientHealthCheck;
import io.quarkus.test.QuarkusUnitTest;

public class McpHealthCheckTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(MockHttpMcpServer.class)
                    .addAsResource(
                            new StringAsset(
                                    """
                                            quarkus.langchain4j.mcp.client1.transport-type=http
                                            quarkus.langchain4j.mcp.client1.url=http://localhost:8081/mock-mcp/sse
                                            quarkus.langchain4j.mcp.client1.log-requests=true
                                            quarkus.langchain4j.mcp.client1.log-responses=true
                                            # short timeout so we can quickly test the health check
                                            quarkus.langchain4j.mcp.client1.ping-timeout=2s
                                            quarkus.log.category."dev.langchain4j".level=DEBUG
                                            quarkus.log.category."io.quarkiverse".level=DEBUG
                                            """),
                            "application.properties"));

    @Inject
    @Readiness
    McpClientHealthCheck healthCheck;

    @Inject
    MockHttpMcpServer server;

    @Test
    public void test() {
        HealthCheckResponse response = healthCheck.call();
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());

        server.stopRespondingToPings();

        response = healthCheck.call();
        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    }
}
