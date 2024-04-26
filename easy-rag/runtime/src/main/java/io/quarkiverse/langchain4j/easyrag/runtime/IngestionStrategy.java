package io.quarkiverse.langchain4j.easyrag.runtime;

public enum IngestionStrategy {

    // TODO: we'd like to also have a "if-empty" and "overwrite" strategy
    // but these require some enhancements to the EmbeddingStore API

    ON,
    OFF

}
