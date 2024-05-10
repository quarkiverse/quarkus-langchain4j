package org.acme.example.bam;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.watsonx.annotation.Deployment;

@RegisterAiService
@Deployment("2")
public interface BAMAiService {

    public String poem(String topic);
}
