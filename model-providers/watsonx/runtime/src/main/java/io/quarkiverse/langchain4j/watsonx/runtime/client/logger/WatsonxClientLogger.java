package io.quarkiverse.langchain4j.watsonx.runtime.client.logger;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.ClientLogger;

import com.ibm.watsonx.ai.core.Json;
import com.ibm.watsonx.ai.core.http.BaseHttpClient;

import io.quarkiverse.langchain4j.runtime.CurlRequestLogger;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

public class WatsonxClientLogger implements ClientLogger {

    private static final Logger log = Logger.getLogger(WatsonxClientLogger.class);
    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile("(\\w+\\s)(\\w{4})(\\w+)(\\w{4})");
    private static final Pattern BASE64_IMAGE_PATTERN = Pattern.compile("(data:[\\w\\/+]+;base64,)(.{15})([^\"]+)");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("\"(api-key|apiKey)\"\\s*:\\s*\"([^\"]+\")",
            Pattern.CASE_INSENSITIVE);

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
    public void logRequest(HttpClientRequest request, Buffer bufferBody, boolean omitBody) {
        if (logRequests && log.isInfoEnabled()) {
            try {

                String headers = null;
                String body = bodyToString(bufferBody);
                StringJoiner joiner = new StringJoiner("\n", "Request:\n", "");
                joiner.add("- method: " + request.getMethod().name());
                joiner.add("- url: " + request.absoluteURI());

                if (nonNull(request.headers())) {
                    headers = inOneLine(request.headers());
                    joiner.add("- headers: " + headers);
                    if (nonNull(body)) {
                        body = maskApiKeysInJsonBody(body);

                        var contentType = ofNullable(request.headers().get("Content-Type"));
                        if (contentType.isPresent() && contentType.get().contains("application/json"))
                            body = Json.prettyPrint(body);

                        joiner.add("- body: " + body);
                    }
                }

                log.infof(joiner.toString());

            } catch (Exception e) {
                log.warn("Failed to log request", e);
            }
        }

        if (logCurl && log.isInfoEnabled()) {
            CurlRequestLogger.logCurl(log, request, bufferBody);
        }
    }

    @Override
    public void logResponse(HttpClientResponse response, boolean redirect) {
        if (!logResponses || !log.isInfoEnabled()) {
            return;
        }
        response.bodyHandler(new Handler<>() {
            @Override
            public void handle(Buffer bufferBody) {

                try {

                    boolean prettyPrint = false;
                    String watsonxAISDKRequestId = ofNullable(
                            response.request().headers().get(BaseHttpClient.REQUEST_ID_HEADER)).orElse("");
                    StringJoiner joiner = new StringJoiner("\n", "Response:\n", "");
                    joiner.add("- " + BaseHttpClient.REQUEST_ID_HEADER + watsonxAISDKRequestId);
                    joiner.add("- url: " + response.request().absoluteURI());
                    joiner.add("- status code: " + response.statusCode());

                    if (nonNull(response.headers())) {
                        joiner.add("- headers: " + inOneLine(response.headers()));

                        var contentType = Optional.<String> empty();

                        if (response.headers().contains("Content-Type"))
                            contentType = ofNullable(response.headers().get("Content-Type"));
                        else if (response.headers().contains("content-type"))
                            contentType = ofNullable(response.headers().get("content-type"));

                        if (contentType.isPresent() && contentType.get().contains("application/json"))
                            prettyPrint = true;
                    }

                    if (nonNull(bufferBody)) {
                        var body = prettyPrint ? Json.prettyPrint(bodyToString(bufferBody)) : bodyToString(bufferBody);
                        joiner.add("- body: " + body);
                    }

                    log.info(joiner.toString());

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

            Matcher matcher = AUTHORIZATION_PATTERN.matcher(authorizationHeaderValue);

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

    private String maskApiKeysInJsonBody(String body) {

        if (body == null || body.isBlank())
            return body;

        Matcher matcher = API_KEY_PATTERN.matcher(body);

        StringBuilder sb = new StringBuilder();
        while (matcher.find())
            matcher.appendReplacement(sb, "\"" + matcher.group(1) + "\": \"***\"");

        matcher.appendTail(sb);
        return sb.isEmpty() ? body : sb.toString();
    }
}
