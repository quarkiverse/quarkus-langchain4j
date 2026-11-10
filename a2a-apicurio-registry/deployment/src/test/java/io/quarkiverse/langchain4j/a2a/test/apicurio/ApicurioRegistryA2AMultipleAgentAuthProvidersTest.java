package io.quarkiverse.langchain4j.a2a.test.apicurio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.a2a.runtime.apicurio.A2AAgentAuthProvider;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Two A2AAgentAuthProvider beans fail the build instead of silently sending no credentials.
 */
public class ApicurioRegistryA2AMultipleAgentAuthProvidersTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(First.class, Second.class))
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.langchain4j.a2a.apicurio-registry.url", "http://localhost:1/apis/registry/v3")
            .assertException(e -> assertThat(e).hasStackTraceContaining("Only one A2AAgentAuthProvider bean is supported"));

    @ApplicationScoped
    public static class First implements A2AAgentAuthProvider {
        @Override
        public String getAuthorization(DiscoveredAgent agent) {
            return "Bearer first";
        }
    }

    @ApplicationScoped
    public static class Second implements A2AAgentAuthProvider {
        @Override
        public String getAuthorization(DiscoveredAgent agent) {
            return "Bearer second";
        }
    }

    @Test
    void buildFails() {
        fail("The build should have failed");
    }
}
