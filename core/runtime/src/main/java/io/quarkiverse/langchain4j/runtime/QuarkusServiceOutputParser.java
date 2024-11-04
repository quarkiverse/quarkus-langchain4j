package io.quarkiverse.langchain4j.runtime;

import java.lang.reflect.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.TypeUtils;
import dev.langchain4j.service.output.ServiceOutputParser;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.smallrye.mutiny.Multi;

public class QuarkusServiceOutputParser extends ServiceOutputParser {
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("(?s)\\{.*\\}|\\[.*\\]");

    @Override
    public String outputFormatInstructions(Type returnType) {
        boolean isOptional = isJavaOptional(returnType);
        Type actualType = isOptional ? unwrapOptionalType(returnType) : returnType;

        Class<?> rawClass = getRawClass(actualType);

        if (rawClass != String.class && rawClass != AiMessage.class && rawClass != TokenStream.class
                && rawClass != ChatMessage.class
                && rawClass != Response.class && !Multi.class.equals(rawClass)) {
            try {
                var schema = this.toJsonSchema(returnType);
                return "You must answer strictly with json according to the following json schema format. Use description metadata to fill data properly: "
                        + schema;
            } catch (Exception e) {
                return "";
            }
        }

        return "";
    }

    public Object parse(Response<AiMessage> response, Type returnType) {
        QuarkusJsonCodecFactory factory = new QuarkusJsonCodecFactory();
        var codec = factory.create();

        if (TypeUtils.typeHasRawClass(returnType, Result.class)) {
            returnType = TypeUtils.resolveFirstGenericParameterClass(returnType);
        }

        Class<?> rawReturnClass = TypeUtils.getRawClass(returnType);

        if (rawReturnClass == Response.class) {
            return response;
        } else {
            AiMessage aiMessage = response.content();
            if (rawReturnClass == AiMessage.class || rawReturnClass == ChatMessage.class) {
                return aiMessage;
            } else {
                String text = aiMessage.text();
                if (rawReturnClass == String.class) {
                    return text;
                } else {
                    try {
                        return codec.fromJson(text, returnType);
                    } catch (Exception var10) {
                        String jsonBlock = this.extractJsonBlock(text);
                        return codec.fromJson(jsonBlock, returnType);
                    }
                }
            }
        }
    }

    private String extractJsonBlock(String text) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : text;
    }

    public String toJsonSchema(Type type) throws Exception {
        Map<String, Object> schema = new HashMap<>();
        boolean isOptional = isJavaOptional(type);
        Type actualType = isOptional ? unwrapOptionalType(type) : type;

        Class<?> rawClass = getRawClass(actualType);

        if (type instanceof WildcardType wildcardType) {
            Type boundType = wildcardType.getUpperBounds().length > 0 ? wildcardType.getUpperBounds()[0]
                    : wildcardType.getLowerBounds()[0];
            return toJsonSchema(boundType);
        }

        if (rawClass == String.class || rawClass == Character.class) {
            schema.put("type", "string");
        } else if (rawClass == Boolean.class || rawClass == boolean.class) {
            schema.put("type", "boolean");
        } else if (Number.class.isAssignableFrom(rawClass) || rawClass.isPrimitive()) {
            schema.put("type", (rawClass == double.class || rawClass == float.class) ? "number" : "integer");
        } else if (Collection.class.isAssignableFrom(rawClass) || rawClass.isArray()) {
            schema.put("type", "array");

            Type elementType = getElementType(type);
            Map<String, Object> itemsSchema = toJsonSchemaMap(elementType);
            schema.put("items", itemsSchema);
        } else if (rawClass == LocalDate.class || rawClass == Date.class) {
            schema.put("type", "string");
            schema.put("format", "date");
        } else if (rawClass == LocalDateTime.class || rawClass == OffsetDateTime.class) {
            schema.put("type", "string");
            schema.put("format", "date-time");
        } else if (rawClass.isEnum()) {
            schema.put("type", "string");
            schema.put("enum", getEnumConstants(rawClass));
        } else {
            schema.put("type", "object");
            Map<String, Object> properties = new HashMap<>();

            List<String> required = new ArrayList<>();
            for (Field field : rawClass.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Type fieldType = field.getGenericType();

                    // Check if the field is Optional and unwrap it if necessary
                    boolean fieldIsOptional = isJavaOptional(fieldType);
                    Type fieldActualType = fieldIsOptional ? unwrapOptionalType(fieldType) : fieldType;

                    Map<String, Object> fieldSchema = toJsonSchemaMap(fieldActualType);
                    properties.put(field.getName(), fieldSchema);

                    if (field.isAnnotationPresent(Description.class)) {
                        Description description = field.getAnnotation(Description.class);
                        fieldSchema.put("description", String.join(",", description.value()));
                    }

                    // Only add to required if it is not Optional
                    if (!fieldIsOptional) {
                        required.add(field.getName());
                    } else {
                        fieldSchema.put("nullable", true); // Mark as nullable in the JSON schema
                    }

                } catch (Exception e) {

                }

            }
            schema.put("properties", properties);
            if (!required.isEmpty()) {
                schema.put("required", required);
            }
        }
        if (isOptional) {
            schema.put("nullable", true);
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(schema); // Convert the schema map to a JSON string
    }

    private boolean isJavaOptional(Type type) {
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            return rawType == Optional.class || rawType == OptionalInt.class || rawType == OptionalLong.class
                    || rawType == OptionalDouble.class;
        }
        return false;
    }

    private Type unwrapOptionalType(Type optionalType) {
        if (optionalType instanceof ParameterizedType) {
            return ((ParameterizedType) optionalType).getActualTypeArguments()[0];
        }
        return optionalType;
    }

    private Class<?> getRawClass(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        } else if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            return Array.newInstance(getRawClass(componentType), 0).getClass();
        } else if (type instanceof WildcardType) {
            Type boundType = ((WildcardType) type).getUpperBounds().length > 0 ? ((WildcardType) type).getUpperBounds()[0]
                    : ((WildcardType) type).getLowerBounds()[0];
            return getRawClass(boundType);
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    private Type getElementType(Type type) {
        if (type instanceof ParameterizedType) {
            return ((ParameterizedType) type).getActualTypeArguments()[0];
        } else if (type instanceof GenericArrayType) {
            return ((GenericArrayType) type).getGenericComponentType();
        } else if (type instanceof Class<?> && ((Class<?>) type).isArray()) {
            return ((Class<?>) type).getComponentType();
        }
        return Object.class; // Fallback for cases where element type cannot be determined
    }

    private Map<String, Object> toJsonSchemaMap(Type type) throws Exception {
        String jsonSchema = toJsonSchema(type);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonSchema, Map.class);
    }

    private List<String> getEnumConstants(Class<?> enumClass) {
        List<String> constants = new ArrayList<>();
        for (Object constant : enumClass.getEnumConstants()) {
            constants.add(constant.toString());
        }
        return constants;
    }
}
