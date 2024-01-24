package io.quarkiverse.langchain4j.watsonx.client;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.jboss.resteasy.reactive.client.api.ClientLogger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationResponse;
import io.quarkiverse.langchain4j.watsonx.bean.WatsonError;
import io.quarkiverse.langchain4j.watsonx.client.filter.BearerRequestFilter;
import io.quarkiverse.langchain4j.watsonx.exception.WatsonException;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.jackson.ClientObjectMapper;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

/**
 * This Microprofile REST client is used as the building block of all the API
 * calls to Watsonx. The implementation is provided by the Reactive REST Client
 * in Quarkus.
 */
@Path("/ml/v1-beta")
@RegisterProvider(BearerRequestFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface WatsonRestApi {

    static Logger logger = Logger.getLogger(WatsonRestApi.class);

    @POST
    @Path("generation/text")
    TextGenerationResponse chat(TextGenerationRequest request, @QueryParam("version") String version) throws WatsonException;

    @ClientExceptionMapper
    static WatsonException toException(jakarta.ws.rs.core.Response response) {

        if (MediaType.TEXT_PLAIN.equals(response.getHeaderString("Content-Type")))
            return new WatsonException(response.readEntity(String.class), response.getStatus());

        try {

            WatsonError ex = response.readEntity(WatsonError.class);

            StringJoiner joiner = new StringJoiner("\n");
            if (ex.errors() != null && ex.errors().size() > 0) {
                for (WatsonError.Error error : ex.errors())
                    joiner.add("%s: %s".formatted(error.code(), error.message()));
            }

            return new WatsonException(joiner.toString(), response.getStatus(), ex);

        } catch (ClientWebApplicationException | ProcessingException e) {
            return new WatsonException(e.getCause(), response.getStatus());
        }
    }

    @ClientObjectMapper
    static ObjectMapper objectMapper(ObjectMapper defaultObjectMapper) {
        return QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER;
    }

    /**
     * Introduce a custom logger as the stock one logs at the DEBUG level by
     * default...
     */
    class WatsonClientLogger implements ClientLogger {

        private static final Logger log = Logger.getLogger(WatsonClientLogger.class);

        private static final Pattern BEARER_PATTERN = Pattern.compile("(Bearer\\s*)(\\w{4})(\\w+)(\\w{4})");

        private final boolean logRequests;
        private final boolean logResponses;

        public WatsonClientLogger(boolean logRequests, boolean logResponses) {
            this.logRequests = logRequests;
            this.logResponses = logResponses;
        }

        @Override
        public void setBodySize(int bodySize) {
            // ignore
        }

        @Override
        public void logRequest(HttpClientRequest request, Buffer body, boolean omitBody) {
            if (!logRequests || !log.isInfoEnabled()) {
                return;
            }
            try {
                log.infof(
                        "Request:\n- method: %s\n- url: %s\n- headers: %s\n- body: %s",
                        request.getMethod(),
                        request.absoluteURI(),
                        inOneLine(request.headers()),
                        bodyToString(body));
            } catch (Exception e) {
                log.warn("Failed to log request", e);
            }
        }

        @Override
        public void logResponse(HttpClientResponse response, boolean redirect) {
            if (!logResponses || !log.isInfoEnabled()) {
                return;
            }
            response.bodyHandler(new Handler<>() {

                @Override
                public void handle(Buffer body) {
                    try {
                        log.infof(
                                "Response:\n- status code: %s\n- headers: %s\n- body: %s",
                                response.statusCode(),
                                inOneLine(response.headers()),
                                bodyToString(body));
                    } catch (Exception e) {
                        log.warn("Failed to log response", e);
                    }
                }
            });
        }

        private String bodyToString(Buffer body) {
            if (body == null) {
                return "";
            }
            return body.toString();
        }

        private String inOneLine(MultiMap headers) {

            return stream(headers.spliterator(), false)
                    .map(header -> {
                        String headerKey = header.getKey();
                        String headerValue = header.getValue();
                        if ("Authorization".equals(headerKey)) {
                            headerValue = maskAuthorizationHeaderValue(headerValue);
                        } else if ("api-key".equals(headerKey)) {
                            headerValue = maskApiKeyHeaderValue(headerValue);
                        }
                        return String.format("[%s: %s]", headerKey, headerValue);
                    })
                    .collect(joining(", "));
        }

        private static String maskAuthorizationHeaderValue(String authorizationHeaderValue) {
            try {

                Matcher matcher = BEARER_PATTERN.matcher(authorizationHeaderValue);

                StringBuilder sb = new StringBuilder();
                while (matcher.find()) {
                    matcher.appendReplacement(sb, matcher.group(1) + matcher.group(2) + "..." + matcher.group(4));
                }

                return sb.toString();
            } catch (Exception e) {
                return "Failed to mask the API key.";
            }
        }

        private static String maskApiKeyHeaderValue(String apiKeyHeaderValue) {
            try {
                if (apiKeyHeaderValue.length() <= 4) {
                    return apiKeyHeaderValue;
                }
                return apiKeyHeaderValue.substring(0, 2)
                        + "..."
                        + apiKeyHeaderValue.substring(apiKeyHeaderValue.length() - 2);
            } catch (Exception e) {
                return "Failed to mask the API key.";
            }
        }
    }
}
