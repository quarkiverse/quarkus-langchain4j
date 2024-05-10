package org.acme.example.bam;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("user")
public interface AiService {

    public String poem(String topic);
}
