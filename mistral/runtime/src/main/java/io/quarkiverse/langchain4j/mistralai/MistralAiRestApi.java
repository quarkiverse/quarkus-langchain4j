package io.quarkiverse.langchain4j.mistralai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.client.SseEvent;
import org.jboss.resteasy.reactive.client.SseEventFilter;
import org.jboss.resteasy.reactive.common.providers.serialisers.AbstractJsonMessageBodyReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import dev.langchain4j.model.mistralai.MistralAiChatCompletionRequest;
import dev.langchain4j.model.mistralai.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingRequest;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingResponse;
import dev.langchain4j.model.mistralai.MistralAiModelResponse;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkus.rest.client.reactive.NotBody;
import io.smallrye.mutiny.Multi;

/**
 * This Microprofile REST client is used as the building block of all the API calls to MistralAI.
 * The implementation is provided by the Reactive REST Client in Quarkus.
 */

@Path("")
@ClientHeaderParam(name = "Authorization", value = "Bearer {token}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RegisterProvider(MistralAiRestApi.MistralAiRestApiJacksonReader.class)
@RegisterProvider(MistralAiRestApi.MistralAiRestApiJacksonWriter.class)
@RegisterProvider(MistralAiRestApi.MistralAiRestApiWriterInterceptor.class)
public interface MistralAiRestApi {

    /**
     * Perform a blocking request for a completion response
     */
    @Path("chat/completions")
    @POST
    MistralAiChatCompletionResponse blockingChatCompletion(MistralAiChatCompletionRequest request, @NotBody String token);

    /**
     * Performs a non-blocking request for a streaming completion request
     */
    @Path("chat/completions")
    @POST
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @SseEventFilter(DoneFilter.class)
    Multi<MistralAiChatCompletionResponse> streamingChatCompletion(MistralAiChatCompletionRequest request,
            @NotBody String token);

    @Path("embeddings")
    @POST
    MistralAiEmbeddingResponse embedding(MistralAiEmbeddingRequest request, @NotBody String token);

    @Path("models")
    @GET
    MistralAiModelResponse models(@NotBody String token);

    /**
     * The point of this is to properly set the {@code stream} value of the request
     * so users don't have to remember to set it manually
     */
    class MistralAiRestApiWriterInterceptor implements WriterInterceptor {
        @Override
        public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
            Object entity = context.getEntity();
            if (entity instanceof MistralAiChatCompletionRequest request) {
                MultivaluedMap<String, Object> headers = context.getHeaders();
                List<Object> acceptList = headers.get(HttpHeaders.ACCEPT);
                if ((acceptList != null) && (acceptList.size() == 1)) {
                    String accept = (String) acceptList.get(0);
                    if (MediaType.APPLICATION_JSON.equals(accept)) {
                        if (Boolean.TRUE.equals(request.getStream())) {
                            context.setEntity(from(request).stream(null).build());
                        }
                    } else if (MediaType.SERVER_SENT_EVENTS.equals(accept)) {
                        if (!Boolean.TRUE.equals(request.getStream())) {
                            context.setEntity(from(request).stream(true).build());
                        }
                    }
                }
            }
            context.proceed();
        }

        private MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder from(
                MistralAiChatCompletionRequest request) {
            var builder = MistralAiChatCompletionRequest.builder();
            builder.model(request.getModel());
            builder.messages(request.getMessages());
            builder.temperature(request.getTemperature());
            builder.topP(request.getTopP());
            builder.maxTokens(request.getMaxTokens());
            builder.stream(request.getStream());
            builder.safePrompt(request.getSafePrompt());
            builder.randomSeed(request.getRandomSeed());
            return builder;
        }
    }

    /**
     * Ensures that the terminal event sent by OpenAI is not processed (as it is not a valid json event)
     */
    class DoneFilter implements Predicate<SseEvent<String>> {

        @Override
        public boolean test(SseEvent<String> event) {
            return !"[DONE]".equals(event.data());
        }
    }

    @Priority(Priorities.USER - 100) // this priority ensures that our Reader has priority over the standard Jackson one
    class MistralAiRestApiJacksonReader extends AbstractJsonMessageBodyReader {

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
    class MistralAiRestApiJacksonWriter implements MessageBodyWriter<Object> {

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
