package io.quarkiverse.langchain4j.runtime.aiservice;

import jakarta.enterprise.inject.spi.CDI;

import io.quarkiverse.langchain4j.response.AiResponseAugmenter;
import io.quarkiverse.langchain4j.response.ResponseAugmenterParams;
import io.smallrye.mutiny.Multi;

public class ResponseAugmenterSupport {

    private ResponseAugmenterSupport() {
        // Avoid direct instantiation
    }

    @SuppressWarnings("unchecked")
    public static <T> T invoke(T object, AiServiceMethodCreateInfo methodCreateInfo,
            ResponseAugmenterParams responseAugmenterParams) {
        if (methodCreateInfo.getResponseAugmenterClassName() == null) {
            return object;
        } else {
            AiResponseAugmenter<T> augmenter = (AiResponseAugmenter<T>) CDI.current()
                    .select(methodCreateInfo.getResponseAugmenter()).get();
            return augmenter.augment(object, responseAugmenterParams);

        }
    }

    public static Multi<?> apply(Multi<?> m, AiServiceMethodCreateInfo methodCreateInfo,
            ResponseAugmenterParams responseAugmenterParams) {
        if (methodCreateInfo.getResponseAugmenterClassName() == null) {
            return m;
        } else {
            AiResponseAugmenter<?> augmenter = CDI.current().select(methodCreateInfo.getResponseAugmenter()).get();
            return augmenter.augment((Multi) m, responseAugmenterParams);
        }
    }
}
