package io.quarkiverse.langchain4j.gemini.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A recursive tool parameter type is described with {@code $defs} and {@code $ref}, which the OpenAPI
 * {@code parameters} schema can't express, so the function declaration carries it as {@code parametersJsonSchema}.
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
    void recursiveParametersAreSentAsJsonSchemaWithDefinitions() {
        GenerateContentRequest request = ContentMapper.map(List.of(UserMessage.from("Hi")),
                List.of(countNodes(), add()), null, null, "gemini-2.5-flash", false);

        JsonNode declarations = JsonMapper.builder().build().valueToTree(request).get("tools").get(0)
                .get("functionDeclarations");
        JsonNode countNodes = declaration(declarations, "countNodes");
        assertFalse(countNodes.has("parameters"));
        JsonNode schema = countNodes.get("parametersJsonSchema");
        String ref = schema.get("properties").get("root").get("$ref").asString();
        assertTrue(ref.startsWith("#/$defs/"), ref);
        assertTrue(schema.get("$defs").has(ref.substring("#/$defs/".length())), schema.toString());

        // A tool without definitions keeps the OpenAPI parameters schema.
        JsonNode add = declaration(declarations, "add");
        assertFalse(add.has("parametersJsonSchema"));
        assertEquals("object", add.get("parameters").get("type").asString());
    }

    private static JsonNode declaration(JsonNode declarations, String name) {
        for (JsonNode declaration : declarations) {
            if (name.equals(declaration.get("name").asString())) {
                return declaration;
            }
        }
        throw new AssertionError("No declaration " + name + " in " + declarations);
    }
}
