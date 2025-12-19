package io.quarkiverse.langchain4j.sample.fileserver;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class FileServerTest {

    @Test
    public void testReading() {
        String content = new FileServer("src/main").readFileContent("src/main/resources/robot-readable.txt");
        Assertions.assertEquals("Hello, AI!", content);
    }

    @Test
    public void testNotAllowed() {
        FileServer fileServer = new FileServer("src/test");
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> fileServer.readFileContent("src/main/resources/robot-readable.txt"));
    }
}