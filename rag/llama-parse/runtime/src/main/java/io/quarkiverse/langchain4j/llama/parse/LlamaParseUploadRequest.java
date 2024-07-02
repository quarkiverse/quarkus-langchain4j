package io.quarkiverse.langchain4j.llama.parse;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;

import jakarta.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.Map;

@RegisterForReflection
public class LlamaParseUploadRequest {

    /** The file data */
    @RestForm
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    private InputStream file;

    /** The file name */
    @RestForm
    @PartType(MediaType.TEXT_PLAIN)
    private String filename;
    
}
