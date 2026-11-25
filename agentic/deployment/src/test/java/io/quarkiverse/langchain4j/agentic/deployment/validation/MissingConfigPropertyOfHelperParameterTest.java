package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.Consumer;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that the application does not start when the configuration property of a {@code @ConfigProperty} parameter
 * of a helper method is missing, as for an injection point, instead of failing when the agent is first used.
 */
public class MissingConfigPropertyOfHelperParameterTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(PricingAgent.class))
            .assertException(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) {
                    assertThat(throwable).hasStackTraceContaining(
                            "Failed to load config value of type class java.lang.String for: pricing-agent.url");
                }
            });

    @Test
    public void test() {
        fail("should never be called");
    }

    public interface PricingAgent {

        @A2AClientAgent(description = "Prices a car remotely", outputKey = "price")
        String price(@V("car") String car);

        @A2AServerUrlSupplier
        static String a2aServerUrl(@ConfigProperty(name = "pricing-agent.url") String url,
                @ConfigProperty(name = "pricing-agent.port", defaultValue = "8080") int port) {
            return url + ":" + port;
        }
    }
}
