package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

public class MetricsWrapper implements AiServiceMethodImplementationSupport.Wrapper {

    @Override
    public Object wrap(AiServiceMethodImplementationSupport.Input input,
            Function<AiServiceMethodImplementationSupport.Input, Object> fun) {
        Optional<AiServiceMethodCreateInfo.MetricsInfo> metricsInfoOpt = input.createInfo.getMetricsInfo();
        if (metricsInfoOpt.isPresent()) {
            AiServiceMethodCreateInfo.MetricsInfo metricsInfo = metricsInfoOpt.get();
            if (metricsInfo.isLongTask()) {
                LongTaskTimer timer = LongTaskTimer.builder(metricsInfo.getName())
                        .description(metricsInfo.getDescription())
                        .publishPercentiles(metricsInfo.getPercentiles())
                        .publishPercentileHistogram(metricsInfo.isHistogram())
                        .tags(metricsInfo.getExtraTags())
                        .register(Metrics.globalRegistry);
                return timer.record(new Supplier<Object>() {
                    @Override
                    public Object get() {
                        return fun.apply(input);
                    }
                });
            } else {
                Timer timer = Timer.builder(metricsInfo.getName())
                        .description(metricsInfo.getDescription())
                        .publishPercentiles(metricsInfo.getPercentiles())
                        .publishPercentileHistogram(metricsInfo.isHistogram())
                        .tags(metricsInfo.getExtraTags())
                        .register(Metrics.globalRegistry);
                return timer.record(new Supplier<Object>() {
                    @Override
                    public Object get() {
                        return fun.apply(input);
                    }
                });
            }
        } else {
            return fun.apply(input);
        }
    }
}
