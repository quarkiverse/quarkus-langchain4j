package org.acme;

import org.eclipse.microprofile.faulttolerance.Retry;

import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface Assistant {
    @Retry(maxRetries = 2)
    String chat(String message);
}
