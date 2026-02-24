package io.quarkiverse.langchain4j.mongodb.runtime;

public enum SimilaritySearch {
    COSINE("cosine"), DOT_PRODUCT("dotProduct"), EUCLIDEAN("euclidean");

    String value;
    SimilaritySearch(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
