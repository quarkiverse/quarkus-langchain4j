package io.quarkiverse.langchain4j.response;

/**
 * Simple ResponseRecord listener, to be implemented by the (advanced) users.
 */
public interface ResponseListener {
    void onResponse(ResponseRecord response);

    default int order() {
        return 0;
    }
}
