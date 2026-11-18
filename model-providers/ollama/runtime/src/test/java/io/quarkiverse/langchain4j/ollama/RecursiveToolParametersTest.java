package io.quarkiverse.langchain4j.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A recursive tool parameter type refers to its definitions with {@code $ref}, so the tool's parameters carry
 * them as {@code $defs}.
 */
class RecursiveToolParametersTest {

    /** The parameters LangChain4j generates for {@code countNodes(TreeNode root)}, where a node has child nodes. */
    static ToolSpecification countNodes() {
        JsonObjectSchema treeNode = JsonObjectSchema.builder()
                .addStringProperty("value")
                .addProperty("children", JsonArraySchema.builder()
                        .items(JsonReferenceSchema.builder().reference("TreeNode").build())
                        .build())
                .build();
        return ToolSpecification.builder()
                .name("countNodes")
                .description("Counts the nodes of a tree")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("root", JsonReferenceSchema.builder().reference("TreeNode").build())
                        .required("root")
                        .definitions(Map.of("TreeNode", treeNode))
                        .build())
                .build();
    }

    static ToolSpecification add() {
        return ToolSpecification.builder()
                .name("add")
                .description("Adds two numbers")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("a")
                        .addIntegerProperty("b")
                        .required("a", "b")
                        .build())
                .build();
    }

    @Test
    void recursiveParametersCarryTheirDefinitions() {
        List<Tool> tools = MessageMapper.toTools(List.of(countNodes(), add()));
        JsonMapper mapper = JsonMapper.builder().build();

        JsonNode countNodes = parameters(mapper, tools, "countNodes");
        String ref = countNodes.get("properties").get("root").get("$ref").asString();
        assertThat(ref).startsWith("#/$defs/");
        assertThat(countNodes.get("$defs").has(ref.substring("#/$defs/".length()))).isTrue();

        // A tool without definitions sends no $defs.
        assertThat(parameters(mapper, tools, "add").has("$defs")).isFalse();
    }

    private static JsonNode parameters(JsonMapper mapper, List<Tool> tools, String name) {
        Tool tool = tools.stream().filter(t -> t.function().name().equals(name)).findFirst().orElseThrow();
        return mapper.valueToTree(tool).get("function").get("parameters");
    }
}
