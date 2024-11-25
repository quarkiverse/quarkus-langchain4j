package io.quarkiverse.langchain4j.response;

import io.smallrye.common.annotation.Experimental;
import io.smallrye.mutiny.Multi;

/**
 * CDI bean willing to manipulate the response of the AI model needs to implement this interface.
 * AI method that wants to use an augmenter should be annotated with {@link ResponseAugmenter}, and indicate the
 * augmenter implementation classname.
 * <p>
 * The default implementation keeps the response unchanged.
 *
 * @param <T> the type of the response
 */
@Experimental("This API is subject to change")
public interface AiResponseAugmenter<T> {

    /**
     * Augment the response.
     *
     * @param response the response to augment
     * @param params the parameters to use for the augmentation
     * @return the augmented response
     */
    default T augment(T response, ResponseAugmenterParams params) {
        return response;
    }

    /**
     * Augment a streamed response.
     *
     * @param stream the stream to augment
     * @param params the parameters to use for the augmentation
     * @return the augmented stream
     */
    default Multi<T> augment(Multi<T> stream, ResponseAugmenterParams params) {
        return stream;
    }

}
