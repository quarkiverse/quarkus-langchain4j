package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Optional;
import java.util.function.Function;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;

public class MetricsCountedWrapper implements AiServiceMethodImplementationSupport.Wrapper {

    private static final String RESULT_TAG_FAILURE_VALUE = "failure";
    private static final String RESULT_TAG_SUCCESS_VALUE = "success";
    private static final String DEFAULT_EXCEPTION_TAG_VALUE = "none";

    @Override
    public Object wrap(AiServiceMethodImplementationSupport.Input input,
            Function<AiServiceMethodImplementationSupport.Input, Object> fun) {
        Optional<AiServiceMethodCreateInfo.MetricsCountedInfo> metricsInfoOpt = input.createInfo.getMetricsCountedInfo();
        if (metricsInfoOpt.isPresent()) {
            AiServiceMethodCreateInfo.MetricsCountedInfo metricsCountedInfo = metricsInfoOpt.get();
            try {
                Object result = fun.apply(input);
                if (!metricsCountedInfo.isRecordFailuresOnly()) {
                    record(metricsCountedInfo, null);
                }
                return result;
            } catch (Throwable e) {
                record(metricsCountedInfo, e);
                throw e;
            }
        } else {
            return fun.apply(input);
        }
    }

    private void record(AiServiceMethodCreateInfo.MetricsCountedInfo metricsCountedInfo, Throwable throwable) {
        Counter.Builder builder = Counter.builder(metricsCountedInfo.getName())
                .tags(metricsCountedInfo.getExtraTags())
                .tag("exception", getExceptionTag(throwable))
                .tag("result", throwable == null ? RESULT_TAG_SUCCESS_VALUE : RESULT_TAG_FAILURE_VALUE);
        String description = metricsCountedInfo.getDescription();
        if (!description.isEmpty()) {
            builder.description(description);
        }
        builder.register(Metrics.globalRegistry).increment();
    }

    private String getExceptionTag(Throwable throwable) {
        if (throwable == null) {
            return DEFAULT_EXCEPTION_TAG_VALUE;
        }
        if (throwable.getCause() == null) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getCause().getClass().getSimpleName();
    }
}
