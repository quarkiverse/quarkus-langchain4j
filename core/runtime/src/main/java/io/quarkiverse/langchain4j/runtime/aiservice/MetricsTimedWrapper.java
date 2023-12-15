package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

public class MetricsTimedWrapper implements AiServiceMethodImplementationSupport.Wrapper {

    @Override
    public Object wrap(AiServiceMethodImplementationSupport.Input input,
            Function<AiServiceMethodImplementationSupport.Input, Object> fun) {
        Optional<AiServiceMethodCreateInfo.MetricsTimedInfo> metricsInfoOpt = input.createInfo.getMetricsTimedInfo();
        if (metricsInfoOpt.isPresent()) {
            AiServiceMethodCreateInfo.MetricsTimedInfo metricsTimedInfo = metricsInfoOpt.get();
            if (metricsTimedInfo.isLongTask()) {
                LongTaskTimer timer = LongTaskTimer.builder(metricsTimedInfo.getName())
                        .description(metricsTimedInfo.getDescription())
                        .publishPercentiles(metricsTimedInfo.getPercentiles())
                        .publishPercentileHistogram(metricsTimedInfo.isHistogram())
                        .tags(metricsTimedInfo.getExtraTags())
                        .register(Metrics.globalRegistry);
                return timer.record(new Supplier<Object>() {
                    @Override
                    public Object get() {
                        return fun.apply(input);
                    }
                });
            } else {
                Timer timer = Timer.builder(metricsTimedInfo.getName())
                        .description(metricsTimedInfo.getDescription())
                        .publishPercentiles(metricsTimedInfo.getPercentiles())
                        .publishPercentileHistogram(metricsTimedInfo.isHistogram())
                        .tags(metricsTimedInfo.getExtraTags())
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
