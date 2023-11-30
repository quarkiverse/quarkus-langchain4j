package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Optional;
import java.util.function.Supplier;

import dev.langchain4j.service.AiServiceContext;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

/**
 * When micrometer metrics are enabled, this is used to record how long calls take
 */
@SuppressWarnings("unused") // the methods are used in generated code
public class MetricsProducingMethodImplementationSupport {

    public static Object implement(AiServiceContext context, AiServiceMethodCreateInfo createInfo, Object[] methodArgs) {
        Optional<AiServiceMethodCreateInfo.MetricsInfo> metricsInfoOpt = createInfo.getMetricsInfo();
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
                        return MethodImplementationSupport.implement(context, createInfo, methodArgs);
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
                        return MethodImplementationSupport.implement(context, createInfo, methodArgs);
                    }
                });
            }
        } else {
            return MethodImplementationSupport.implement(context, createInfo, methodArgs);
        }
    }
}
