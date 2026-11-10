package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that tool parameters whose types refer to themselves get a {@code $ref} into the tool's {@code $defs}
 * instead of making the build recurse forever, matching {@code dev.langchain4j.internal.JsonSchemaElementUtils}.
 */
public class ToolRecursiveParameterTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    public static class TreeNode {
        public String name;
        public List<TreeNode> children;
    }

    public static class Employee {
        public String name;
        public Employee manager;
    }

    public static class Department {
        public String name;
        public Team team;
    }

    public static class Team {
        public Department department;
    }

    public static class Address {
        public String street;
    }

    public static class Order {
        public Address shipTo;
        public Address billTo;
    }

    public static class RecursiveTools {

        @Tool("save a tree")
        public void saveTree(TreeNode root) {
        }

        @Tool("save an employee")
        public void saveEmployee(Employee employee) {
        }

        @Tool("save a department")
        public void saveDepartment(Department department) {
        }

        @Tool("place an order")
        public void placeOrder(Order order) {
        }

        @Tool("save an optional tree")
        public void saveOptionalTree(Optional<TreeNode> root) {
        }
    }

    private static JsonObjectSchema parametersOf(String toolName) {
        List<ToolMethodCreateInfo> infos = ToolsRecorder.getMetadata().get(RecursiveTools.class.getName());
        assertThat(infos).isNotNull();
        return infos.stream()
                .filter(i -> i.toolSpecification().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tool named " + toolName))
                .toolSpecification()
                .parameters();
    }

    @Test
    void recursionThroughACollectionUsesAReference() {
        JsonObjectSchema parameters = parametersOf("saveTree");
        JsonObjectSchema root = (JsonObjectSchema) parameters.properties().get("root");

        assertThat(root.properties().get("name")).isInstanceOf(JsonStringSchema.class);
        JsonArraySchema children = (JsonArraySchema) root.properties().get("children");
        String reference = ((JsonReferenceSchema) children.items()).reference();

        assertThat(parameters.definitions()).containsOnlyKeys(reference);
        JsonObjectSchema definition = (JsonObjectSchema) parameters.definitions().get(reference);
        assertThat(definition.properties()).containsOnlyKeys("name", "children");
    }

    @Test
    void optionalRecursiveParameterUsesAReferenceAndIsNotRequired() {
        JsonObjectSchema parameters = parametersOf("saveOptionalTree");
        JsonObjectSchema root = (JsonObjectSchema) parameters.properties().get("root");

        assertThat(root.properties()).containsOnlyKeys("name", "children");
        JsonArraySchema children = (JsonArraySchema) root.properties().get("children");
        String reference = ((JsonReferenceSchema) children.items()).reference();

        assertThat(parameters.definitions()).containsOnlyKeys(reference);
        assertThat(parameters.required()).doesNotContain("root");
    }

    @Test
    void directSelfReferenceUsesAReference() {
        JsonObjectSchema parameters = parametersOf("saveEmployee");
        JsonObjectSchema employee = (JsonObjectSchema) parameters.properties().get("employee");

        String reference = ((JsonReferenceSchema) employee.properties().get("manager")).reference();
        assertThat(parameters.definitions()).containsOnlyKeys(reference);
    }

    @Test
    void mutualRecursionReferencesTheTypeThatIsReachedAgain() {
        JsonObjectSchema parameters = parametersOf("saveDepartment");
        JsonObjectSchema department = (JsonObjectSchema) parameters.properties().get("department");
        JsonObjectSchema team = (JsonObjectSchema) department.properties().get("team");

        String reference = ((JsonReferenceSchema) team.properties().get("department")).reference();
        assertThat(parameters.definitions()).containsOnlyKeys(reference);
        assertThat(((JsonObjectSchema) parameters.definitions().get(reference)).properties())
                .containsOnlyKeys("name", "team");
    }

    @Test
    void typeUsedTwiceWithoutRecursionIsInlinedBothTimes() {
        JsonObjectSchema parameters = parametersOf("placeOrder");
        JsonObjectSchema order = (JsonObjectSchema) parameters.properties().get("order");

        assertThat(order.properties().get("shipTo")).isInstanceOf(JsonObjectSchema.class);
        assertThat(order.properties().get("billTo")).isInstanceOf(JsonObjectSchema.class);
        assertThat(parameters.definitions()).isNullOrEmpty();
    }
}
