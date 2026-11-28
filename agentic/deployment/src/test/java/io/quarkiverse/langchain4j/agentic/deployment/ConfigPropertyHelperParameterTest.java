package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that a parameter of a static helper method is resolved from CDI when its qualifier is
 * {@code @ConfigProperty}, which comes from a jar without a Jandex index that the extension indexes, that the bean is
 * looked up by the type of the parameter with its type arguments, which the producer of an {@code Optional}
 * configuration value needs, and that the build does not warn about the {@code @Dependent} producer of the
 * configuration value.
 */
public class ConfigPropertyHelperParameterTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("pricing-agent.host", "localhost")
            .overrideRuntimeConfigKey("pricing-agent.port", "1")
            .overrideRuntimeConfigKey("pricing-agent.version", "2")
            .overrideRuntimeConfigKey("pricing-agent.path", "/pricing")
            .setLogRecordPredicate(new Predicate<LogRecord>() {
                @Override
                public boolean test(LogRecord record) {
                    return record.getLevel().intValue() >= Level.WARNING.intValue();
                }
            })
            .assertLogRecords(new Consumer<List<LogRecord>>() {
                @Override
                public void accept(List<LogRecord> records) {
                    // the advice to give the bean a normal scope does not apply to a configuration value
                    for (LogRecord record : records) {
                        assertThat(String.valueOf(record.getMessage())).doesNotContain("@Dependent bean");
                    }
                }
            });

    public interface PricingAgent {
        @A2AClientAgent(description = "Prices a car remotely", outputKey = "price")
        String price(@V("car") String car);

        @A2AServerUrlSupplier
        static String a2aServerUrl(@ConfigProperty(name = "pricing-agent.host") String host,
                @ConfigProperty(name = "pricing-agent.port") int port,
                @ConfigProperty(name = "pricing-agent.version") Integer version,
                @ConfigProperty(name = "pricing-agent.path") Optional<String> path,
                @ConfigProperty(name = "pricing-agent.query") Optional<String> query) {
            return "http://" + host + ":" + port + "/v" + version + path.orElse("") + query.orElse("");
        }
    }

    public interface Pricing {
        @SequenceAgent(outputKey = "price", subAgents = PricingAgent.class)
        String price(@V("car") String car);
    }

    @Inject
    Pricing pricing;

    @Test
    void configPropertyOfAnA2AServerUrlSupplier() {
        // there is no A2A server: the URL made of the configuration values is used to fetch the agent card
        assertThatThrownBy(() -> pricing.price("Fiat 500"))
                .hasStackTraceContaining(
                        "Failed to fetch the agent card of A2A agent 'price' from http://localhost:1/v2/pricing");
    }
}
