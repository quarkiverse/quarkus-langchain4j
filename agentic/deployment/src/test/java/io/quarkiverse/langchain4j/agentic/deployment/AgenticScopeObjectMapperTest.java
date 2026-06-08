package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that an {@link AgenticScope} can be serialized through the application's managed
 * {@link ObjectMapper} and round-tripped.
 * <p>
 * The companion unit test {@code AgenticScopeSerializationTest} (agentic/runtime) evidences why this is
 * needed: a vanilla ObjectMapper without our customizer throws {@code InvalidDefinitionException}. Here we
 * assert the managed mapper, customized by {@code AgenticScopeObjectMapperCustomizer}, marshals it correctly.
 */
public class AgenticScopeObjectMapperTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever");

    @Inject
    ObjectMapper objectMapper;

    private DefaultAgenticScope newScope() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("topic", "dragons and wizards");
        scope.writeState("style", "fantasy");
        scope.writeState("score", 0.9);
        return scope;
    }

    @Test
    void managedObjectMapperRoundTripsAgenticScope() throws Exception {
        String json = objectMapper.writeValueAsString(newScope());

        DefaultAgenticScope restored = objectMapper.readValue(json, DefaultAgenticScope.class);

        assertThat(restored.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(restored.readState("style")).isEqualTo("fantasy");
        assertThat(restored.<Double> readState("score", 0.0)).isEqualTo(0.9);
    }

    @Test
    void managedObjectMapperSerializesAgenticScopeNestedInAnotherObject() throws Exception {
        Wrapper wrapper = new Wrapper();
        wrapper.name = "story-creator";
        wrapper.scope = newScope();

        String json = objectMapper.writeValueAsString(wrapper);
        Wrapper restored = objectMapper.readValue(json, Wrapper.class);

        assertThat(restored.name).isEqualTo("story-creator");
        assertThat(restored.scope.readState("topic")).isEqualTo("dragons and wizards");
    }

    public static class Wrapper {
        public String name;
        public AgenticScope scope;
    }
}
