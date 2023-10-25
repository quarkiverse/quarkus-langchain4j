package io.quarkiverse.langchain4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import dev.langchain4j.internal.Json;
import dev.langchain4j.spi.json.JsonCodecFactory;
import io.quarkus.arc.Arc;

public class QuarkusJsonCodecFactory implements JsonCodecFactory {

    @Override
    public Json.JsonCodec create() {
        return new Codec();
    }

    private static class Codec implements Json.JsonCodec {

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
                return ObjectMapperHolder.MAPPER.readValue(json, type);
            } catch (JsonProcessingException e) {
                if ((e instanceof JsonParseException) && (type.isEnum())) {
                    // this is the case where Langchain4j simply passes the string value of the enum to Json.fromJson()
                    // and Jackson does not handle it
                    Class<? extends Enum> enumClass = type.asSubclass(Enum.class);
                    return (T) Enum.valueOf(enumClass, json);
                }
                throw new UncheckedIOException(e);
            }
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
                    .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            WRITER = MAPPER.writerWithDefaultPrettyPrinter();
        }
    }

}
