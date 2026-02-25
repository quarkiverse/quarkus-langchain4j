package io.quarkiverse.langchain4j.gemini.common;

import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
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
    protected final boolean useGoogleSearch;

    public BaseGeminiChatModel(String modelId, Double temperature, Integer maxOutputTokens, Integer topK, Double topP,
            ResponseFormat responseFormat, List<ChatModelListener> listeners, Long thinkingBudget,
            boolean includeThoughts, boolean useGoogleSearch) {
        this.modelId = modelId;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.topK = topK;
        this.topP = topP;
        this.responseFormat = responseFormat;
        this.listeners = listeners;
        this.thinkingBudget = thinkingBudget;
        this.includeThoughts = includeThoughts;
        this.useGoogleSearch = useGoogleSearch;
    }

    /**
     * Detects and returns the schema based on the given response format.
     * If the response format type is JSON or if a JSON schema is provided,
     * a corresponding {@code Schema} object is created and returned. Otherwise, returns {@code null}.
     *
     * @param effectiveResponseFormat nullable detected {@code ResponseFormat}
     * @return the detected {@code Schema} object; otherwise, {@code null}
     */
    protected Schema detectSchema(ResponseFormat effectiveResponseFormat) {
        if (effectiveResponseFormat != null &&
                (effectiveResponseFormat.type().equals(ResponseFormatType.JSON)
                        || effectiveResponseFormat.jsonSchema() != null)) {
            if (effectiveResponseFormat.jsonSchema() != null
                    && effectiveResponseFormat.jsonSchema().rootElement() instanceof JsonRawSchema) {
                return null;
            }
            return SchemaMapper.fromJsonSchemaToSchema(effectiveResponseFormat.jsonSchema());
        }
        return null;
    }

    /**
     * Detects and returns the raw JSON schema as a map if the response format uses {@link JsonRawSchema}.
     *
     * @param effectiveResponseFormat nullable detected {@code ResponseFormat}
     * @return the raw schema as a {@code Map}, or {@code null} if not a raw schema
     */
    protected Map<String, Object> detectRawSchema(ResponseFormat effectiveResponseFormat) {
        if (effectiveResponseFormat != null
                && effectiveResponseFormat.jsonSchema() != null
                && effectiveResponseFormat.jsonSchema().rootElement() instanceof JsonRawSchema jsonRawSchema) {
            try {
                return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(
                        jsonRawSchema.schema(), new TypeReference<>() {
                        });
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Unable to parse raw JSON schema", e);
            }
        }
        return null;
    }

    /**
     * Computes the MIME type based on the provided response format.
     * If the response format is null or of type TEXT, returns "text/plain".
     * If the response format is of type JSON and contains a JSON schema with a root element
     * that is an instance of JsonEnumSchema, returns "text/x.enum".
     * If detected schema and raw schema are both null, logs a warning and defaults to "text/plain".
     * Otherwise, returns "application/json".
     *
     * @param responseFormat nullable {@code ResponseFormat}
     * @param schema nullable {@code Schema}
     * @param rawSchema nullable raw JSON schema {@code Map}
     * @return the computed MIME type as a string
     */
    protected String computeMimeType(ResponseFormat responseFormat, Schema schema, Map<String, Object> rawSchema) {
        if (responseFormat == null || ResponseFormatType.TEXT.equals(responseFormat.type())) {
            return "text/plain";
        }

        if (ResponseFormatType.JSON.equals(responseFormat.type()) &&
                responseFormat.jsonSchema() != null &&
                responseFormat.jsonSchema().rootElement() != null &&
                responseFormat.jsonSchema().rootElement() instanceof JsonEnumSchema) {
            return "text/x.enum";
        }

        if (schema == null && rawSchema == null) {
            log.warn("Schema is null while computing MIME type suggesting 'application/json'. Defaulting to 'text/plain'.");
            return "text/plain";
        }

        return "application/json";
    }
}
