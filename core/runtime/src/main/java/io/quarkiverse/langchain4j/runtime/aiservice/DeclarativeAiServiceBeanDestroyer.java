package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Map;

import jakarta.enterprise.context.spi.CreationalContext;

import org.jboss.logging.Logger;

import io.quarkus.arc.BeanDestroyer;

public class DeclarativeAiServiceBeanDestroyer implements BeanDestroyer<AutoCloseable> {

    private static final Logger log = Logger.getLogger(DeclarativeAiServiceBeanDestroyer.class);

    @Override
    public void destroy(AutoCloseable instance, CreationalContext<AutoCloseable> creationalContext,
            Map<String, Object> params) {
        try {
            instance.close();
        } catch (Exception e) {
            log.error("Unable to close " + instance);
        }
    }
}
