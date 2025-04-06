package io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import software.amazon.awssdk.http.SdkHttpRequest;

public class JaxRsSdkHttpClientHelper {

    public static Invocation.Builder createAndPrepareInvocationBuilder(final Client delegate, final SdkHttpRequest request) {
        WebTarget target = delegate.target(request.getUri());
        Invocation.Builder invocationBuilder = target.request();

        final MultivaluedHashMap<String, Object> headerMap = new MultivaluedHashMap<>();

        for (var headers : request.headers().entrySet()) {
            List<String> values = headers.getValue();
            if ((values != null) && (!values.isEmpty())) {
                headerMap.put(headers.getKey(), new ArrayList<>(values));
            }
        }

        invocationBuilder.headers(headerMap);

        return invocationBuilder;
    }

    public static Map<String, List<String>> getResponseHeaders(final Response response) {
        final MultivaluedMap<String, Object> headers = response.getHeaders();

        final Map<String, List<String>> result = new HashMap<>();

        for (Map.Entry<String, List<Object>> entry : headers.entrySet()) {
            final String key = entry.getKey();
            final List<Object> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                List<String> resultValues = new ArrayList<>(values.size());
                for (Object value : values) {
                    resultValues.add(String.valueOf(value));
                }
                result.put(key, resultValues);
            }
        }
        return result;
    }
}
