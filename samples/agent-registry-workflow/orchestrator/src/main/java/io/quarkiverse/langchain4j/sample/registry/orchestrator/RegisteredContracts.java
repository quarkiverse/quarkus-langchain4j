package io.quarkiverse.langchain4j.sample.registry.orchestrator;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

/** Observes version metadata for the walkthrough. Registry enforces compatibility when content is published. */
@ApplicationScoped
public class RegisteredContracts {
    @RestClient
    ContractClient client;

    public List<Contract> observe() {
        return List.of(read("weather-tools", "weather"), read("default", "summarizer"), read("default", "translator"));
    }

    private Contract read(String group, String artifact) {
        try {
            JsonNode metadata = client.metadata(group, artifact);
            return new Contract(group, artifact, metadata.path("version").asText(),
                    metadata.path("globalId").asLong(), metadata.path("state").asText());
        } catch (WebApplicationException e) {
            throw new ServiceUnavailableException("Cannot read required contract " + group + "/" + artifact);
        }
    }

    public record Contract(String groupId, String artifactId, String version, long globalId, String state) {
    }
}
