package io.quarkiverse.langchain4j.samples;

import static dev.langchain4j.data.document.FileSystemDocumentLoader.loadDocument;

import java.io.File;

import dev.langchain4j.data.document.Document;

public class DocumentFromTextCreationExample {

    Document createDocument(File file) {
        return loadDocument(file.toPath());
    }
}
