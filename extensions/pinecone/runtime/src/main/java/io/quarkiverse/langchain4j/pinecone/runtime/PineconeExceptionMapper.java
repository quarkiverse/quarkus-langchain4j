package io.quarkiverse.langchain4j.pinecone.runtime;

import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

public class PineconeExceptionMapper implements ResponseExceptionMapper<RuntimeException> {
    @Override
    public RuntimeException toThrowable(Response response) {
        // FIXME: do this for some more status codes?
        if (response.getStatus() == 400) {
            return new RuntimeException("Pinecone returned 400, error: " + response.readEntity(String.class));
        }
        return null;
    }
}
