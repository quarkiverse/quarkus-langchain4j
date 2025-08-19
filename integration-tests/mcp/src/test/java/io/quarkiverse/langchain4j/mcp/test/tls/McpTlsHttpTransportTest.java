package io.quarkiverse.langchain4j.mcp.test.tls;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.startServerHttp;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.mcp.test.McpServerHelper;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;

@Certificates(baseDir = "target/certs", certificates = {
        @Certificate(name = "mcp", password = "password", formats = { Format.PKCS12 }, client = true),
        @Certificate(name = "mcp-bad", password = "password", formats = { Format.PKCS12 }, client = true)
})
class McpTlsHttpTransportTest extends McpTlsTestBase {

    private static Process process;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.tls.tls-client-correct.key-store.p12.path", "target/certs/mcp-client-keystore.p12")
            .overrideConfigKey("quarkus.tls.tls-client-correct.key-store.p12.password", "password")
            .overrideConfigKey("quarkus.tls.tls-client-correct.trust-store.p12.path", "target/certs/mcp-client-truststore.p12")
            .overrideConfigKey("quarkus.tls.tls-client-correct.trust-store.p12.password", "password")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.url", "https://localhost:8083/mcp/sse")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.tls-configuration-name", "tls-client-correct")
            // 'tls-client-bad' and therefore MCP client 'client2' is using a truststore that does not trust the server
            .overrideConfigKey("quarkus.tls.tls-client-bad.key-store.p12.path", "target/certs/mcp-client-keystore.p12")
            .overrideConfigKey("quarkus.tls.tls-client-bad.key-store.p12.password", "password")
            .overrideConfigKey("quarkus.tls.tls-client-bad.trust-store.p12.path", "target/certs/mcp-bad-client-truststore.p12")
            .overrideConfigKey("quarkus.tls.tls-client-bad.trust-store.p12.password", "password")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.transport-type", "http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.url", "https://localhost:8083/mcp/sse")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.tool-execution-timeout", "3s")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.tls-configuration-name", "tls-client-bad")

            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse\".level", "DEBUG");

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        String[] serverTlsConfiguration = new String[] {
                "-Dquarkus.tls.key-store.p12.path=target/certs/mcp-keystore.p12",
                "-Dquarkus.tls.key-store.p12.password=password",
                "-Dquarkus.tls.trust-store.p12.path=target/certs/mcp-server-truststore.p12",
                "-Dquarkus.tls.trust-store.p12.password=password" };
        process = McpServerHelper.startServerHttp("tls_mcp_server.java", 8082, 8083, serverTlsConfiguration);
    }

    @AfterAll
    static void teardown() {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
