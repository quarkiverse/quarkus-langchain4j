package io.quarkiverse.langchain4j.compaction.runtime;

import java.lang.annotation.Annotation;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Singleton;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContextQualifier;

/**
 * Produces the {@link ChatMemoryProvider} of the bean injecting it. Quarkus LangChain4j injects the provider once per
 * AI service, into the service context bean, which is qualified with the service interface name: that name gives the
 * compaction profile. Anything else (another bean, a programmatic lookup) gets the default profile.
 */
@Singleton
public class ChatMemoryProviderProducer {

    @Produces
    @Dependent
    ChatMemoryProvider provider(InjectionPoint injectionPoint, CompactionProfiles profiles,
            CompactionProfileUsage usage) {
        Bean<?> bean = injectionPoint == null ? null : injectionPoint.getBean();
        if (bean != null) {
            for (Annotation qualifier : bean.getQualifiers()) {
                if (qualifier instanceof QuarkusAiServiceContextQualifier service) {
                    String profile = usage.profiles().get(service.value());
                    if (profile != null) {
                        return profiles.provider(profile);
                    }
                }
            }
        }
        return profiles.defaultProvider();
    }
}
