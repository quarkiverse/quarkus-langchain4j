package io.quarkiverse.langchain4j.sample.registry.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.ServiceUnavailableException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class RequiredContractsTest {
    @Test
    void reportsObservedVersionsAndRejectsDeprecatedOrMissingCapabilities() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RequiredContracts contracts = new RequiredContracts();
        contracts.client = mock(ContractClient.class);
        for (String name : new String[] { "weather", "summarizer", "translator" }) {
            String group = name.equals("weather") ? "weather-tools" : "default";
            when(contracts.client.metadata(group, name)).thenReturn(mapper.readTree(
                    "{\"version\":\"2\",\"globalId\":42,\"state\":\"ENABLED\"}"));
            when(contracts.client.content(group, name, "2")).thenReturn(mapper.readTree(name.equals("weather")
                    ? "{\"name\":\"getWeather\"}"
                    : "{\"skills\":[{\"id\":\"" + name + "\"}]}"));
        }
        assertEquals(3, contracts.check().size());
        assertEquals(new RequiredContracts.Contract("default", "translator", "2", 42, "ENABLED"), contracts.check().get(2));
        when(contracts.client.metadata("default", "translator")).thenReturn(mapper.readTree("{\"state\":\"DEPRECATED\"}"));
        assertThrows(ServiceUnavailableException.class, contracts::check);
        when(contracts.client.metadata("default", "translator"))
                .thenReturn(mapper.readTree("{\"state\":\"ENABLED\",\"version\":\"2\"}"));
        when(contracts.client.content("default", "translator", "2")).thenReturn(mapper.readTree("{\"skills\":[]}"));
        assertThrows(ServiceUnavailableException.class, contracts::check);
    }
}
