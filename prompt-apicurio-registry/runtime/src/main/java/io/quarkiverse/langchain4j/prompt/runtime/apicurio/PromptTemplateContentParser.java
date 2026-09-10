package io.quarkiverse.langchain4j.prompt.runtime.apicurio;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.quarkiverse.langchain4j.prompt.PromptTemplateResolutionException;
import io.quarkiverse.langchain4j.prompt.PromptVariable;
import io.quarkiverse.langchain4j.prompt.ResolvedPromptTemplate;

/**
 * Parses the content of an Apicurio Registry {@code PROMPT_TEMPLATE} artifact into a {@link ResolvedPromptTemplate}.
 * <p>
 * The following content formats are supported:
 * <ul>
 * <li>JSON documents with a {@code template} field (and optional {@code variables} / {@code inputs})</li>
 * <li>YAML documents with the same structure</li>
 * <li>Prompty-style documents: a YAML front matter delimited by {@code ---} lines followed by the template body</li>
 * <li>Plain text, in which case the whole content is the template and no variables are declared</li>
 * </ul>
 * Variables can be declared either as a map from variable name to schema
 * ({@code {"question": {"type": "string", "required": true}}}) or as a JSON schema object
 * ({@code {"type": "object", "properties": {...}, "required": [...]}}). Scalar values are interpreted as default
 * values, which is how Prompty declares sample inputs.
 */
public final class PromptTemplateContentParser {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final String FRONT_MATTER_DELIMITER = "---";

    private PromptTemplateContentParser() {
    }

    public static ResolvedPromptTemplate parse(String content, String version) {
        if (content == null) {
            throw new PromptTemplateResolutionException("The prompt template content is empty");
        }
        String trimmed = content.strip();
        if (trimmed.isEmpty()) {
            throw new PromptTemplateResolutionException("The prompt template content is empty");
        }

        if (trimmed.startsWith("{")) {
            return fromDocument(readTree(JSON, trimmed, "JSON"), null, version);
        }
        if (trimmed.startsWith(FRONT_MATTER_DELIMITER)) {
            int bodyStart = findFrontMatterEnd(trimmed);
            if (bodyStart > 0) {
                String frontMatter = trimmed.substring(FRONT_MATTER_DELIMITER.length(), bodyStart);
                String body = trimmed.substring(bodyStart);
                int lineEnd = body.indexOf('\n');
                body = lineEnd == -1 ? "" : body.substring(lineEnd + 1);
                JsonNode node = frontMatter.isBlank() ? JSON.createObjectNode() : readTree(YAML, frontMatter, "YAML");
                return fromDocument(node, body, version);
            }
        }
        JsonNode yaml = tryReadYaml(trimmed);
        if (yaml != null && yaml.isObject() && yaml.has("template")) {
            return fromDocument(yaml, null, version);
        }
        return new ResolvedPromptTemplate(content, version, Map.of());
    }

    private static int findFrontMatterEnd(String content) {
        int from = FRONT_MATTER_DELIMITER.length();
        while (true) {
            int idx = content.indexOf('\n' + FRONT_MATTER_DELIMITER, from);
            if (idx == -1) {
                return -1;
            }
            int afterDelimiter = idx + 1 + FRONT_MATTER_DELIMITER.length();
            if (afterDelimiter == content.length() || content.charAt(afterDelimiter) == '\n'
                    || content.charAt(afterDelimiter) == '\r') {
                return idx + 1;
            }
            from = afterDelimiter;
        }
    }

    private static JsonNode readTree(ObjectMapper mapper, String content, String format) {
        try {
            return mapper.readTree(content);
        } catch (JsonProcessingException e) {
            throw new PromptTemplateResolutionException("The prompt template content is not valid " + format, e);
        }
    }

    private static JsonNode tryReadYaml(String content) {
        try {
            return YAML.readTree(content);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static ResolvedPromptTemplate fromDocument(JsonNode document, String body, String version) {
        if (!document.isObject()) {
            throw new PromptTemplateResolutionException("The prompt template content must be an object");
        }
        String text;
        JsonNode templateNode = document.get("template");
        if (templateNode != null && templateNode.isTextual()) {
            text = templateNode.asText();
        } else if (body != null) {
            text = body;
        } else {
            throw new PromptTemplateResolutionException(
                    "The prompt template content does not contain a 'template' field: is the artifact a PROMPT_TEMPLATE?");
        }

        Map<String, PromptVariable> variables = new LinkedHashMap<>();
        addVariables(document.get("variables"), variables);
        addVariables(document.get("inputs"), variables);

        String resolvedVersion = version;
        if (resolvedVersion == null && document.hasNonNull("version")) {
            resolvedVersion = document.get("version").asText();
        }
        return new ResolvedPromptTemplate(text, resolvedVersion, variables);
    }

    private static void addVariables(JsonNode node, Map<String, PromptVariable> variables) {
        if (node == null || !node.isObject()) {
            return;
        }
        JsonNode properties = node.get("properties");
        if (properties != null && properties.isObject()) {
            // JSON schema style: { "type": "object", "properties": {...}, "required": [...] }
            Set<String> required = new LinkedHashSet<>();
            JsonNode requiredNode = node.get("required");
            if (requiredNode != null && requiredNode.isArray()) {
                for (JsonNode r : requiredNode) {
                    required.add(r.asText());
                }
            }
            for (Iterator<Map.Entry<String, JsonNode>> it = properties.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> entry = it.next();
                variables.putIfAbsent(entry.getKey(), toVariable(entry.getKey(), entry.getValue(),
                        required.contains(entry.getKey())));
            }
            return;
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> entry = it.next();
            variables.putIfAbsent(entry.getKey(), toVariable(entry.getKey(), entry.getValue(), false));
        }
    }

    private static PromptVariable toVariable(String name, JsonNode schema, boolean requiredFromSchema) {
        if (schema == null || schema.isNull()) {
            return new PromptVariable(name, null, false, null, null);
        }
        if (!schema.isObject()) {
            // Prompty style: the value is a sample / default value
            return new PromptVariable(name, inferType(schema), false, toJava(schema), null);
        }
        String type = schema.hasNonNull("type") ? schema.get("type").asText() : null;
        boolean required = requiredFromSchema
                || (schema.hasNonNull("required") && schema.get("required").isBoolean()
                        && schema.get("required").asBoolean());
        Object defaultValue = schema.hasNonNull("default") ? toJava(schema.get("default")) : null;
        String description = schema.hasNonNull("description") ? schema.get("description").asText() : null;
        return new PromptVariable(name, type, required, defaultValue, description);
    }

    private static String inferType(JsonNode node) {
        if (node.isTextual()) {
            return "string";
        }
        if (node.isIntegralNumber()) {
            return "integer";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        if (node.isArray()) {
            return "array";
        }
        return null;
    }

    private static Object toJava(JsonNode node) {
        try {
            return JSON.treeToValue(node, Object.class);
        } catch (JsonProcessingException e) {
            return node.asText();
        }
    }
}
