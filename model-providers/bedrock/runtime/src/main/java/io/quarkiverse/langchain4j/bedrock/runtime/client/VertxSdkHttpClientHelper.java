package io.quarkiverse.langchain4j.bedrock.runtime.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import software.amazon.awssdk.http.SdkHttpRequest;

public class VertxSdkHttpClientHelper {

    private VertxSdkHttpClientHelper() {
    }

    public static RequestOptions buildRequestOptions(SdkHttpRequest sdkRequest) {
        RequestOptions requestOptions = new RequestOptions()
                .setAbsoluteURI(sdkRequest.getUri().toString())
                .setMethod(HttpMethod.valueOf(sdkRequest.method().name()));

        for (Map.Entry<String, List<String>> entry : sdkRequest.headers().entrySet()) {
            for (String value : entry.getValue()) {
                requestOptions.addHeader(entry.getKey(), value);
            }
        }

        return requestOptions;
    }

    public static Map<String, List<String>> convertHeaders(HttpClientResponse response) {
        Map<String, List<String>> headers = new HashMap<>();
        for (String name : response.headers().names()) {
            headers.put(name, response.headers().getAll(name));
        }
        return headers;
    }
}
