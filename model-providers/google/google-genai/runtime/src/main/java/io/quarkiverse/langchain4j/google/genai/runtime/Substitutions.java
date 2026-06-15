package io.quarkiverse.langchain4j.google.genai.runtime;

import java.io.IOException;
import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import okhttp3.Interceptor;
import okhttp3.Response;

@TargetClass(value = OkHttpOpenTelemetryInterceptor.class, onlyWith = Target_OkHttpOpenTelemetryInterceptor.IsOpenTelemetryAbsent.class)
final class Target_OkHttpOpenTelemetryInterceptor {

    @Substitute
    public Response intercept(Interceptor.Chain chain) throws IOException {
        return chain.proceed(chain.request());
    }

    static final class IsOpenTelemetryAbsent implements BooleanSupplier {

        @Override
        public boolean getAsBoolean() {
            try {
                Class.forName("io.opentelemetry.api.GlobalOpenTelemetry");
                return false;
            } catch (ClassNotFoundException ignored) {
                return true;
            }
        }
    }
}
