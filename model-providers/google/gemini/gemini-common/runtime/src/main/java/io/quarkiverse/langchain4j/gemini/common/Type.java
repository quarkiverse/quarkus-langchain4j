package io.quarkiverse.langchain4j.gemini.common;

public enum Type {
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    ARRAY,
    OBJECT;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
