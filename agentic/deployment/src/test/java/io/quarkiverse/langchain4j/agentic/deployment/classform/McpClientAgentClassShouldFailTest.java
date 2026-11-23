package io.quarkiverse.langchain4j.agentic.deployment.classform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.declarative.McpClientSupplier;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.V;
import io.quarkus.test.QuarkusExtensionTest;

public class McpClientAgentClassShouldFailTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class).addClass(WeatherAgent.class);
                }
            })
            .assertException(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) {
                    assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("The method 'fetchWeather' of class '" + WeatherAgent.class.getName()
                                    + "' is annotated with @McpClientAgent, but an MCP client agent must be declared as "
                                    + "an interface");
                }
            });

    @Test
    void test() {
        fail("should never be called");
    }

    @ApplicationScoped
    public static class WeatherAgent {

        @McpClientAgent(toolName = "getWeatherForecast", description = "Fetches the weather forecast", outputKey = "weather")
        public native String fetchWeather(@V("destination") String destination);

        @McpClientSupplier
        public static Object mcpClient() {
            return null;
        }
    }
}
