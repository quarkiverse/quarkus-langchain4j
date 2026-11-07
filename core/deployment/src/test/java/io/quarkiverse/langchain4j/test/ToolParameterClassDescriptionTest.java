package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.output.structured.Description;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that a class-level {@link Description} on a complex tool-parameter type reaches the generated
 * {@link JsonObjectSchema}, matching {@code dev.langchain4j.internal.JsonSchemaElementUtils}.
 */
public class ToolParameterClassDescriptionTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Description("A customer order")
    public static class Order {

        public String id;

        // no field-level description: the description must come from Address itself
        public Address shipTo;

        @Description("Where to send the invoice")
        public Address billTo;
    }

    @Description({ "A postal address,", "street level" })
    public static class Address {

        public String street;
    }

    @Description("How urgently to ship")
    public enum Priority {
        STANDARD,
        EXPRESS
    }

    public static class DescribedTypeTool {

        @Tool("place an order")
        public void place(Order order) {
        }

        @Tool("place an order with an overriding description")
        public void placeOverridden(@P("The order to place") Order order) {
        }

        @Tool("prioritize")
        public void prioritize(Priority priority) {
        }
    }

    private static JsonObjectSchema parametersOf(String toolName) {
        List<ToolMethodCreateInfo> infos = ToolsRecorder.getMetadata().get(DescribedTypeTool.class.getName());
        assertThat(infos).isNotNull();
        return infos.stream()
                .filter(i -> i.toolSpecification().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tool named " + toolName))
                .toolSpecification()
                .parameters();
    }

    @Test
    void classLevelDescriptionIsUsedWhenTheParameterHasNone() {
        JsonObjectSchema order = (JsonObjectSchema) parametersOf("place").properties().get("order");

        assertThat(order.description()).isEqualTo("A customer order");
    }

    @Test
    void parameterLevelDescriptionWinsOverTheClassLevelOne() {
        JsonObjectSchema order = (JsonObjectSchema) parametersOf("placeOverridden").properties().get("order");

        assertThat(order.description()).isEqualTo("The order to place");
    }

    @Test
    void classLevelDescriptionIsUsedForNestedFieldsAndJoinsMultipleLines() {
        JsonObjectSchema order = (JsonObjectSchema) parametersOf("place").properties().get("order");

        // the field carries no @Description, so Address's own one applies
        assertThat(((JsonObjectSchema) order.properties().get("shipTo")).description())
                .isEqualTo("A postal address, street level");
        // a field-level @Description still takes precedence over the field type's
        assertThat(((JsonObjectSchema) order.properties().get("billTo")).description())
                .isEqualTo("Where to send the invoice");
    }

    @Test
    void classLevelDescriptionIsUsedForEnums() {
        JsonEnumSchema priority = (JsonEnumSchema) parametersOf("prioritize").properties().get("priority");

        assertThat(priority.description()).isEqualTo("How urgently to ship");
        assertThat(priority.enumValues()).containsExactly("STANDARD", "EXPRESS");
    }
}
