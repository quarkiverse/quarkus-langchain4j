package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that the {@code @P} attributes are reflected in the generated {@link JsonObjectSchema},
 * matching {@code dev.langchain4j.agent.tool.ToolSpecifications}.
 */
public class ToolParameterAnnotationTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    public static class ParameterTool {

        @Tool("required by default")
        public void requiredByDefault(String noAnnotation,
                @P("shorthand description") String shorthand,
                @P(description = "named description") String named) {
        }

        @Tool("explicitly optional")
        public void explicitlyOptional(@P(value = "optional", required = false) String optional,
                @P(value = "with default", defaultValue = "5") int withDefault,
                @P("plain") String plain) {
        }

        @Tool("descriptions")
        public void descriptions(@P("only value") String onlyValue,
                @P(description = "only description") String onlyDescription,
                @P(required = false) String noDescription,
                @P(value = "") String blankDescription) {
        }
    }

    private static JsonObjectSchema parametersOf(String toolName) {
        List<ToolMethodCreateInfo> infos = ToolsRecorder.getMetadata().get(ParameterTool.class.getName());
        assertThat(infos).isNotNull();
        return infos.stream()
                .filter(i -> i.toolSpecification().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tool named " + toolName))
                .toolSpecification()
                .parameters();
    }

    @Test
    void parameterIsRequiredUnlessOptOut() {
        // @P without an explicit required attribute must keep the annotation's default of true
        assertThat(parametersOf("requiredByDefault").required())
                .containsExactlyInAnyOrder("noAnnotation", "shorthand", "named");
    }

    @Test
    void requiredFalseAndDefaultValueMakeParameterOptional() {
        JsonObjectSchema parameters = parametersOf("explicitlyOptional");
        assertThat(parameters.required()).containsExactly("plain");
        assertThat(parameters.properties()).containsKeys("optional", "withDefault", "plain");
    }

    @Test
    void descriptionComesFromEitherValueOrDescription() {
        Map<String, JsonSchemaElement> properties = parametersOf("descriptions").properties();

        assertThat(((JsonStringSchema) properties.get("onlyValue")).description())
                .isEqualTo("only value");
        assertThat(((JsonStringSchema) properties.get("onlyDescription")).description())
                .isEqualTo("only description");
        // a @P that carries no usable description must leave the description unset rather than blank
        assertThat(((JsonStringSchema) properties.get("noDescription")).description())
                .isNull();
        assertThat(((JsonStringSchema) properties.get("blankDescription")).description())
                .isNull();
    }
}
