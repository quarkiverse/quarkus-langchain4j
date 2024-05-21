package io.quarkiverse.langchain4j.sample.chatbot;

public class MovieSchemaSupport {

    public static String getSchemaString() {
        String columnsStr = MovieTableIntegrator.schemaStr;
        if (columnsStr.isEmpty()) {
            throw new IllegalStateException("MovieSchemaSupport#getSchemaString called too early");
        }
        return columnsStr;
    }
}
