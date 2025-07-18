package io.quarkiverse.langchain4j;

import jakarta.enterprise.inject.spi.CDI;

import dev.langchain4j.spi.classloading.ClassInstanceFactory;

public class QuarkusClassInstanceFactory implements ClassInstanceFactory {
    @Override
    public <T> T getInstanceOfClass(Class<T> clazz) {
        return CDI.current().select(clazz).get();
    }
}
