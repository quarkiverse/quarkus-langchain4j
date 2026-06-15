package io.quarkiverse.langchain4j.google.genai.runtime;

import java.io.IOException;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp interceptor that propagates OpenTelemetry context headers (e.g. {@code traceparent}, {@code tracestate})
 * into outgoing HTTP requests.
 */
class OkHttpOpenTelemetryInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder requestBuilder = original.newBuilder();

        GlobalOpenTelemetry.get()
                .getPropagators()
                .getTextMapPropagator()
                .inject(Context.current(), requestBuilder, Holder.SETTER);

        return chain.proceed(requestBuilder.build());
    }

    // use this holder in order to prevent GraalVM from following the reference at points-to analysis time
    private static class Holder {

        private static final TextMapSetter<Request.Builder> SETTER = new TextMapSetter<>() {
            @Override
            public void set(Request.Builder carrier, String key, String value) {
                if (carrier != null) {
                    carrier.header(key, value);
                }
            }
        };
    }
}
