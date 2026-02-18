package io.quarkiverse.langchain4j.watsonx.client;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.ClientLogger;

import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory.ObjectMapperHolder;
import io.quarkiverse.langchain4j.runtime.CurlRequestLogger;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

public class WatsonxClientLogger implements ClientLogger {

    private static final Logger log = Logger.getLogger(WatsonxClientLogger.class);
    private static final Pattern BEARER_PATTERN = Pattern.compile("(Bearer\\s*)(\\w{4})(\\w+)(\\w{4})");
    private static final Pattern BASE64_IMAGE_PATTERN = Pattern.compile("(data:.+;base64,)(.{15})(.+)(.{15})([\\s\\S]*)");

    private final boolean logRequests;
    private final boolean logResponses;
    private final boolean logCurl;

    public WatsonxClientLogger(boolean logRequests, boolean logResponses) {
        this(logRequests, logResponses, false);
    }

    public WatsonxClientLogger(boolean logRequests, boolean logResponses, boolean logCurl) {
        this.logRequests = logRequests;
        this.logResponses = logResponses;
        this.logCurl = logCurl;
    }

    public WatsonxClientLogger(Optional<Boolean> logRequests, Optional<Boolean> logResponses) {
        this.logRequests = logRequests.orElse(false);
        this.logResponses = logResponses.orElse(false);
        this.logCurl = false;
    }

    @Override
    public void setBodySize(int bodySize) {
        // ignore
    }

    @Override
    public void logRequest(HttpClientRequest request, Buffer body, boolean omitBody) {
        if (logRequests && log.isInfoEnabled()) {
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
        if (logCurl) {
            CurlRequestLogger.logCurl(log, request, body);
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
                String prettyBody;
                try {
                    prettyBody = ObjectMapperHolder.WRITER
                            .writeValueAsString(ObjectMapperHolder.MAPPER.readTree(bodyToString(body)));
                } catch (Exception e) {
                    prettyBody = bodyToString(body);
                }
                try {

                    log.infof(
                            "Response:\n- status code: %s\n- headers: %s\n- body: %s",
                            response.statusCode(),
                            inOneLine(response.headers()),
                            prettyBody);
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
        return formatBase64ImageForLogging(body.toString());
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

    private static String formatBase64ImageForLogging(String body) {
        try {

            if (body == null || body.isBlank())
                return body;

            Matcher matcher = BASE64_IMAGE_PATTERN.matcher(body);

            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(sb,
                        matcher.group(1) + matcher.group(2) + "..." + matcher.group(4) + matcher.group(5));
            }

            return sb.isEmpty() ? body : sb.toString();
        } catch (Exception e) {
            return "Failed to format the base64 image value.";
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
