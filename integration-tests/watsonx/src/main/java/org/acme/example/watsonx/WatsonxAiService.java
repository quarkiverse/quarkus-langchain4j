package org.acme.example.bam;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.watsonx.annotation.Deployment;

@RegisterAiService(modelName = "1")
@Deployment("1")
public interface WatsonxAiService {

    public String poem(String topic);
}
