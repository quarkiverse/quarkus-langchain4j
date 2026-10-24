package io.quarkiverse.langchain4j.mockapi.apptests;

import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface Assistant {

    String chat(String msg);
}
