package io.quarkiverse.langchain4j.llama3;

public interface ProgressReporter {

    void update(String filename, long sizeDownloaded, long totalSize);
}
