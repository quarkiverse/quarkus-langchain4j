package io.quarkiverse.langchain4j.anthropic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import jakarta.annotation.Priority;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.common.providers.serialisers.AbstractJsonMessageBodyReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import dev.langchain4j.model.anthropic.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.AnthropicStreamingData;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.smallrye.mutiny.Multi;

@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RegisterProvider(AnthropicRestApi.AnthropicRestApiJacksonReader.class)
@RegisterProvider(AnthropicRestApi.AnthropicRestApiJacksonWriter.class)
@RegisterProvider(AnthropicRestApi.AnthropicRestApiWriterInterceptor.class)
public interface AnthropicRestApi {
    String API_KEY_HEADER = "x-api-key";

    @Path("/messages")
    @POST
    AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request, @BeanParam ApiMetadata apiMetadata);

    @Path("/messages")
    @POST
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<AnthropicStreamingData> streamMessage(AnthropicCreateMessageRequest request, @BeanParam ApiMetadata apiMetadata);

    class ApiMetadata {
        @HeaderParam(API_KEY_HEADER)
        public final String apiKey;

        @HeaderParam("anthropic-version")
        public final String anthropicVersion;

        private ApiMetadata(String apiKey, String anthropicVersion) {
            if ((apiKey == null) || apiKey.isBlank()) {
                throw new IllegalArgumentException("apiKey cannot be null or blank");
            }

            if ((anthropicVersion == null) || anthropicVersion.isBlank()) {
                throw new IllegalArgumentException("anthropicVersion cannot be null or blank");
            }

            this.apiKey = apiKey;
            this.anthropicVersion = anthropicVersion;
        }

        public static ApiMetadata.Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String apiKey;
            private String anthropicVersion;

            public ApiMetadata build() {
                return new ApiMetadata(this.apiKey, this.anthropicVersion);
            }

            public ApiMetadata.Builder apiKey(String apiKey) {
                this.apiKey = apiKey;
                return this;
            }

            public ApiMetadata.Builder anthropicVersion(String anthropicVersion) {
                this.anthropicVersion = anthropicVersion;
                return this;
            }
        }
    }

    /**
     * The point of this is to properly set the {@code stream} value of the request
     * so users don't have to remember to set it manually
     */
    class AnthropicRestApiWriterInterceptor implements WriterInterceptor {
        @Override
        public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
            var entity = context.getEntity();

            if (entity instanceof AnthropicCreateMessageRequest request) {
                var headers = context.getHeaders();
                var acceptList = headers.get(HttpHeaders.ACCEPT);

                if ((acceptList != null) && (acceptList.size() == 1)) {
                    var accept = acceptList.get(0);

                    if (MediaType.APPLICATION_JSON.equals(accept)) {
                        if (Boolean.TRUE.equals(request.isStream())) {
                            context.setEntity(request.toBuilder().stream(false).build());
                        }
                    } else if (MediaType.SERVER_SENT_EVENTS.equals(accept)) {
                        if (!Boolean.TRUE.equals(request.isStream())) {
                            context.setEntity(request.toBuilder().stream(true).build());
                        }
                    }
                }
            }

            context.proceed();
        }
    }

    @Priority(Priorities.USER - 100) // this priority ensures that our Reader has priority over the standard Jackson one
    class AnthropicRestApiJacksonReader extends AbstractJsonMessageBodyReader {

        /**
         * We need a custom version of the Jackson provider because reading SSE values does not work properly with
         * {@code @ClientObjectMapper} due to the lack of a complete context in those requests
         */
        @Override
        public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
                throws IOException, WebApplicationException {
            return ObjectMapperHolder.READER
                    .forType(ObjectMapperHolder.READER.getTypeFactory().constructType(genericType != null ? genericType : type))
                    .readValue(entityStream);
        }
    }

    @Priority(Priorities.USER + 100) // this priority ensures that our Writer has priority over the standard Jackson one
    class AnthropicRestApiJacksonWriter implements MessageBodyWriter<Object> {
        @Override
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return true;
        }

        @Override
        public void writeTo(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
                throws IOException, WebApplicationException {
            entityStream.write(ObjectMapperHolder.MAPPER.writeValueAsString(o).getBytes(StandardCharsets.UTF_8));
        }
    }

    class ObjectMapperHolder {
        public static final ObjectMapper MAPPER = QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER;

        private static final ObjectReader READER = MAPPER.reader();
    }
}
