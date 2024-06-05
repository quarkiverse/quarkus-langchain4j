package org.acme.example.cache;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MultipleEmbeddingTest {

    @Inject
    AiService aiService;

    //@Test
    void defaultModel() {

        // TODO: This test doesn't work. Look at the BeansProcessor class to understand why.
        // Line 170!
        aiService.poem("dog");
        aiService.poem("dog");
    }
}
