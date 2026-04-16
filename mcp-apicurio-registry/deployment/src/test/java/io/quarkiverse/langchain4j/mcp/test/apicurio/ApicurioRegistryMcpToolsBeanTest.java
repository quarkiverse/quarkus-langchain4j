package io.quarkiverse.langchain4j.mcp.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that the ApicurioRegistryMcpTools bean and the ToolProvider bean
 * are correctly created and injected when the extension is on the classpath.
 */
public class ApicurioRegistryMcpToolsBeanTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.langchain4j.mcp.apicurio-registry.url=http://localhost:8080/apis/registry/v3\n"),
                            "application.properties"));

    @Inject
    ApicurioRegistryMcpTools apicurioRegistryMcpTools;

    @Inject
    ToolProvider toolProvider;

    @Test
    void beanIsInjected() {
        assertThat(apicurioRegistryMcpTools).isNotNull();
    }

    @Test
    void toolProviderIsInjected() {
        assertThat(toolProvider).isNotNull();
    }
}
