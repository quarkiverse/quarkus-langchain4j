package io.quarkiverse.langchain4j.llama.parse;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;

import java.io.InputStream;

@RegisterForReflection
public class LlamaParseUploadResponse {

    private String id;
    
}
