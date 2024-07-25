package io.quarkiverse.langchain4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;

import dev.langchain4j.internal.Json;
import dev.langchain4j.spi.json.JsonCodecFactory;
import io.quarkus.arc.Arc;

public class QuarkusJsonCodecFactory implements JsonCodecFactory {

    @Override
    public Json.JsonCodec create() {
        return new Codec();
    }

    private static class Codec implements Json.JsonCodec {

        private static final Pattern sanitizePattern = Pattern.compile("(\\\\n)*```(json)?(\\\\n)*");

        @Override
        public String toJson(Object o) {
            try {
                return ObjectMapperHolder.WRITER.writeValueAsString(o);
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public <T> T fromJson(String json, Class<T> type) {
            try {
                String sanitizedJson = sanitize(json, type);
                return ObjectMapperHolder.MAPPER.readValue(sanitizedJson, type);
            } catch (JsonProcessingException e) {
                if ((e instanceof JsonParseException) && (type.isEnum())) {
                    // this is the case where LangChain4j simply passes the string value of the enum to Json.fromJson()
                    // and Jackson does not handle it
                    Class<? extends Enum> enumClass = type.asSubclass(Enum.class);
                    return (T) Enum.valueOf(enumClass, json);
                }
                throw new UncheckedIOException(e);
            }
        }

        private <T> String sanitize(String original, Class<T> type) {
            if (String.class.equals(type)) {
                return original;
            }

            Matcher matcher = sanitizePattern.matcher(original);
            if (matcher.find()) {
                return matcher.replaceAll("");
            }
            return original;
        }

        @Override
        public InputStream toInputStream(Object o, Class<?> type) throws IOException {
            return new ByteArrayInputStream(ObjectMapperHolder.WRITER.writeValueAsBytes(o));
        }

    }

    public static class ObjectMapperHolder {
        public static final ObjectMapper MAPPER;
        public static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {
        };
        private static final ObjectWriter WRITER;

        static {
            MAPPER = Arc.container().instance(ObjectMapper.class).get()
                    .copy()
                    .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                    .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                    .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
            WRITER = MAPPER.writerWithDefaultPrettyPrinter();
        }
    }

    public static class SnakeCaseObjectMapperHolder {
        public static final ObjectMapper MAPPER = Arc.container().instance(ObjectMapper.class).get()
                .copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
    }

}
