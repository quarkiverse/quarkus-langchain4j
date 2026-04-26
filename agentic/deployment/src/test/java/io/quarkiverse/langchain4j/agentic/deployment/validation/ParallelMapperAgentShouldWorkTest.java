package io.quarkiverse.langchain4j.agentic.deployment.validation;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class ParallelMapperAgentShouldWorkTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(Person.class, PersonAstrologyAgent.class, BatchHoroscopeAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Test
    void test() {
        // we don't need to do anything as we just wanted to make sure that validation doesn't fail the build
    }

    @Inject
    BatchHoroscopeAgent agent;

    public record Person(String name, String sign) {
    }

    public interface PersonAstrologyAgent {
        @SystemMessage("""
                You are an astrologist that generates horoscopes based on the user's name and zodiac sign.
                """)
        @UserMessage("""
                Generate the horoscope for {{person}}.
                The person has a name and a zodiac sign. Use both to create a personalized horoscope.
                """)
        @Agent(description = "An astrologist that generates horoscopes for a person", outputKey = "horoscope")
        String horoscope(@V("person") Person person);
    }

    public interface BatchHoroscopeAgent {

        @ParallelMapperAgent(subAgent = PersonAstrologyAgent.class)
        List<String> generateHoroscopes(@V("persons") List<Person> persons);
    }
}
