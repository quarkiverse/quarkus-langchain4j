package io.quarkiverse.langchain4j.bedrock.runtime;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.utils.IoUtils;

// copied from langchain4j. Sadly not visible for reuse. We add some log level checks to improve performance.
// remove sensitive headers and unify the logging format
public class AwsLoggingInterceptor implements ExecutionInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AwsLoggingInterceptor.class);

    private static final String REDACTED = "REDACTED";

    private final boolean logRequests;
    private final boolean logResponses;
    private final boolean logBody;

    public AwsLoggingInterceptor(final boolean logRequests, final boolean logResponses, final boolean logBody) {
        this.logRequests = logRequests;
        this.logResponses = logResponses;
        this.logBody = logBody;
    }

    @Override
    public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
        if (logRequests && LOGGER.isDebugEnabled()) {
            LOGGER.debug("AWS SDK Operation: {}", context.request().getClass().getSimpleName());
        }
    }

    @Override
    public void beforeTransmission(Context.BeforeTransmission context, ExecutionAttributes executionAttributes) {
        SdkHttpRequest request = context.httpRequest();
        String body = null;
        if (logRequests) {
            if (request.method() == SdkHttpMethod.POST && request instanceof SdkHttpFullRequest sdkHttpFullRequest) {
                try {
                    final ContentStreamProvider csp = sdkHttpFullRequest.contentStreamProvider().orElse(null);
                    if (nonNull(csp))
                        body = IoUtils.toUtf8String(csp.newStream());
                } catch (IOException e) {
                    LOGGER.warn("Unable to obtain request body", e);
                }
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Request:\n- method: {}\n- url: {}\n- headers: {}\n- body: {}\n- queryParams: {}",
                        request.method(),
                        request.getUri(),
                        sanitizeHeaders(request.headers()),
                        includeBody(body),
                        request.rawQueryParameters());
            }
        }
    }

    @Override
    public Optional<InputStream> modifyHttpResponseContent(
            Context.ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
        byte[] content = null;
        if (logResponses) {
            try {
                if (LOGGER.isDebugEnabled()) {
                    var statusCode = context.httpResponse().statusCode();
                    var headers = sanitizeHeaders(context.httpResponse().headers());

                    final InputStream responseContentStream = context.responseBody().orElse(InputStream.nullInputStream());
                    content = IoUtils.toByteArray(responseContentStream);

                    LOGGER.debug("Response:\n- status: {}\n- headers: {}\n- body: {}",
                            statusCode, headers, new String(content, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                LOGGER.warn("Unable to obtain response body", e);
            }
        }
        return isNull(content) ? Optional.empty() : Optional.of(new ByteArrayInputStream(content));
    }

    private Map<String, List<String>> sanitizeHeaders(final Map<String, List<String>> headers) {
        var map = new HashMap<>(headers);
        if (map.containsKey("Authorization")) {
            map.put("Authorization", List.of(REDACTED));
        }
        if (map.containsKey("X-Amz-Security-Token")) {
            map.put("X-Amz-Security-Token", List.of(REDACTED));
        }
        return map;
    }

    private Object includeBody(final Object body) {
        if (logBody) {
            return body;
        } else {
            return REDACTED;
        }
    }
}
