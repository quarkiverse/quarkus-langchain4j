package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.test.QuarkusUnitTest;

public class McpDisabledTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.enabled=false
                            quarkus.langchain4j.mcp.client1.transport-type=http
                            """),
                            "application.properties"));

    @Inject
    @McpClientName("client1")
    Instance<McpClient> clientCDIInstance;

    @Inject
    Instance<ToolProvider> toolProviderCDIInstance;

    @Test
    public void test() {
        assertThat(clientCDIInstance.isResolvable()).isFalse();
        assertThat(toolProviderCDIInstance.isResolvable()).isFalse();
    }
}
