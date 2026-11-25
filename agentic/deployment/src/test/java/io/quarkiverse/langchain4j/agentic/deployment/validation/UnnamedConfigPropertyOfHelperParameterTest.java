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
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that the build fails when a {@code @ConfigProperty} parameter of a helper method does not declare the name
 * of the property: unlike an injection point, the parameter gives the property no default name.
 */
public class UnnamedConfigPropertyOfHelperParameterTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(PricingAgent.class))
            .assertException(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) {
                    assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("The @ConfigProperty parameter no.0 of method 'a2aServerUrl'")
                            .hasMessageContaining("must declare the name of the property");
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
        static String a2aServerUrl(@ConfigProperty String url) {
            return url;
        }
    }
}
