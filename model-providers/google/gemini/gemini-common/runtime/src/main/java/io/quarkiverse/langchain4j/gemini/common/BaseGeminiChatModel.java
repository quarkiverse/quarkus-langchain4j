package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

public class BaseGeminiChatModel {

    protected static final Logger log = Logger.getLogger(GeminiChatLanguageModel.class);
    protected final String modelId;
    protected final Double temperature;
    protected final Integer maxOutputTokens;
    protected final Integer topK;
    protected final Double topP;
    protected final ResponseFormat responseFormat;
    protected final List<ChatModelListener> listeners;
    protected final Long thinkingBudget;
    protected final boolean includeThoughts;
    protected final ThinkingLevel thinkingLevel;
    protected final boolean useGoogleSearch;

    public BaseGeminiChatModel(String modelId, Double temperature, Integer maxOutputTokens, Integer topK, Double topP,
            ResponseFormat responseFormat, List<ChatModelListener> listeners, Long thinkingBudget,
            boolean includeThoughts, ThinkingLevel thinkingLevel, boolean useGoogleSearch) {
        if (thinkingBudget != null && thinkingLevel != null) {
            throw new IllegalArgumentException(
                    "Cannot configure both thinkingBudget and thinkingLevel. "
                            + "thinkingBudget remains for Gemini 2.5 and upcoming models return 400 if it is sent. "
                            + "Use thinkingLevel (minimal, low, medium, high) for Gemini 3 and later.");
        }
        this.modelId = modelId;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.topK = topK;
        this.topP = topP;
        this.responseFormat = responseFormat;
        this.listeners = listeners;
        this.thinkingBudget = thinkingBudget;
        this.includeThoughts = includeThoughts;
        this.thinkingLevel = thinkingLevel;
        this.useGoogleSearch = useGoogleSearch;
    }

    protected void applyThinkingConfig(GenerationConfig.Builder generationConfigBuilder) {
        if (thinkingBudget != null || includeThoughts || thinkingLevel != null) {
            generationConfigBuilder
                    .thinkingConfig(new ThinkingConfig(thinkingBudget, includeThoughts, thinkingLevel));
        }
    }

    /**
     * Detects and returns the schema based on the given response format.
     * If the response format type is JSON or if a JSON schema is provided,
     * a corresponding {@code Schema} object is created and returned. Otherwise, or if the JSON schema
     * cannot be mapped to a {@code Schema} (see {@link #detectRawSchema(ResponseFormat)}), returns {@code null}.
     *
     * @param effectiveResponseFormat nullable detected {@code ResponseFormat}
     * @return the detected {@code Schema} object; otherwise, {@code null}
     */
    protected Schema detectSchema(ResponseFormat effectiveResponseFormat) {
        if (effectiveResponseFormat != null &&
                (effectiveResponseFormat.type().equals(ResponseFormatType.JSON)
                        || effectiveResponseFormat.jsonSchema() != null)) {
            if (effectiveResponseFormat.jsonSchema() != null
                    && !SchemaMapper.isSupported(effectiveResponseFormat.jsonSchema().rootElement())) {
                return null;
            }
            return SchemaMapper.fromJsonSchemaToSchema(effectiveResponseFormat.jsonSchema());
        }
        return null;
    }

    /**
     * Detects and returns the raw JSON schema as a map, to be sent as {@code responseJsonSchema}.
     * This is the case if the response format uses {@link JsonRawSchema}, or a JSON schema that cannot be
     * mapped to a {@code Schema}, such as one with {@code $ref} or {@code anyOf} for a recursive or
     * polymorphic type.
     *
     * @param effectiveResponseFormat nullable detected {@code ResponseFormat}
     * @return the raw schema as a {@code Map}, or {@code null} if the schema is mapped to a {@code Schema}
     */
    protected Map<String, Object> detectRawSchema(ResponseFormat effectiveResponseFormat) {
        if (effectiveResponseFormat == null || effectiveResponseFormat.jsonSchema() == null) {
            return null;
        }
        JsonSchemaElement rootElement = effectiveResponseFormat.jsonSchema().rootElement();
        if (rootElement instanceof JsonRawSchema jsonRawSchema) {
            try {
                return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(
                        jsonRawSchema.schema(), new TypeReference<>() {
                        });
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Unable to parse raw JSON schema", e);
            }
        }
        if (!SchemaMapper.isSupported(rootElement)) {
            return JsonSchemaElementUtils.toMap(rootElement);
        }
        return null;
    }

    /**
     * Computes the MIME type based on the provided response format.
     * If the response format is null or of type TEXT, returns "text/plain".
     * If the response format is of type JSON and contains a JSON schema with a root element
     * that is an instance of JsonEnumSchema, returns "text/x.enum".
     * Otherwise, returns "application/json", also when no JSON schema is given.
     *
     * @param responseFormat nullable {@code ResponseFormat}
     * @return the computed MIME type as a string
     */
    protected String computeMimeType(ResponseFormat responseFormat) {
        if (responseFormat == null || ResponseFormatType.TEXT.equals(responseFormat.type())) {
            return "text/plain";
        }

        if (ResponseFormatType.JSON.equals(responseFormat.type()) &&
                responseFormat.jsonSchema() != null &&
                responseFormat.jsonSchema().rootElement() != null &&
                responseFormat.jsonSchema().rootElement() instanceof JsonEnumSchema) {
            return "text/x.enum";
        }

        return "application/json";
    }
}
