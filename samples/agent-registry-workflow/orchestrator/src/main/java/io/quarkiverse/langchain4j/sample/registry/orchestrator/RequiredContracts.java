package io.quarkiverse.langchain4j.sample.registry.orchestrator;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

/** Consumer-side lifecycle policy. Registry does not enforce this policy on runtime traffic. */
@ApplicationScoped
public class RequiredContracts {
    @RestClient
    ContractClient client;

    public List<Contract> check() {
        List<Contract> contracts = new ArrayList<>();
        contracts.add(require("weather-tools", "weather", "getWeather"));
        contracts.add(require("default", "summarizer", "summarizer"));
        contracts.add(require("default", "translator", "translator"));
        return List.copyOf(contracts);
    }

    private Contract require(String group, String artifact, String capability) {
        try {
            JsonNode metadata = client.metadata(group, artifact);
            String state = metadata.path("state").asText();
            if (!"ENABLED".equals(state)) {
                throw new ServiceUnavailableException("Required contract " + group + "/" + artifact
                        + " is " + state + "; restore an enabled contract before starting a briefing");
            }
            JsonNode content = client.content(group, artifact, metadata.path("version").asText());
            boolean found = capability.equals(content.path("name").asText());
            if (!"weather-tools".equals(group)) {
                found = false;
                for (JsonNode skill : content.path("skills")) {
                    found |= capability.equals(skill.path("id").asText());
                }
            }
            if (!found) {
                throw new ServiceUnavailableException("Required capability missing from contract " + group + "/" + artifact);
            }
            return new Contract(group, artifact, metadata.path("version").asText(),
                    metadata.path("globalId").asLong(), state);
        } catch (WebApplicationException e) {
            if (e instanceof ServiceUnavailableException) {
                throw e;
            }
            throw new ServiceUnavailableException("Cannot read required contract " + group + "/" + artifact);
        }
    }

    public record Contract(String groupId, String artifactId, String version, long globalId, String state) {
    }
}
