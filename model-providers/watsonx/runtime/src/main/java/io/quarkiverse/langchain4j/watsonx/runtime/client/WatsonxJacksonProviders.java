package io.quarkiverse.langchain4j.watsonx.runtime.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;

import org.jboss.resteasy.reactive.common.providers.serialisers.AbstractJsonMessageBodyReader;

import com.fasterxml.jackson.databind.ObjectReader;

import io.quarkiverse.langchain4j.watsonx.runtime.spi.JsonProvider;

/**
 * JSON providers for the watsonx REST Client interfaces, backed by the Jackson 2 mapper the watsonx SDK module is
 * registered with. Since Quarkus 4 the REST Client's own Jackson integration is Jackson 3, and the SDK module only
 * exists for Jackson 2, so these providers replace the former {@code @ClientObjectMapper} methods.
 */
public final class WatsonxJacksonProviders {

    private static final ObjectReader READER = JsonProvider.MAPPER.reader();

    private WatsonxJacksonProviders() {
    }

    @Priority(Priorities.USER - 100) // ahead of the standard Jackson provider
    public static class Reader extends AbstractJsonMessageBodyReader {

        @Override
        public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
                throws IOException, WebApplicationException {
            return READER.forType(READER.getTypeFactory().constructType(genericType != null ? genericType : type))
                    .readValue(entityStream);
        }
    }

    @Priority(Priorities.USER + 100) // writers are sorted the opposite way from readers: the highest value wins
    public static class Writer implements MessageBodyWriter<Object> {

        @Override
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return true;
        }

        @Override
        public void writeTo(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
                throws IOException, WebApplicationException {
            entityStream.write(JsonProvider.MAPPER.writeValueAsString(o).getBytes(StandardCharsets.UTF_8));
        }
    }
}
