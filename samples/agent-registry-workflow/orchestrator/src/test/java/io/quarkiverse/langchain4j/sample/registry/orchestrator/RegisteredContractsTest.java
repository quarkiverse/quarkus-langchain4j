package io.quarkiverse.langchain4j.sample.registry.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class RegisteredContractsTest {
    @Test
    void reportsStoredVersionsWithoutApplyingALifecyclePolicy() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RegisteredContracts contracts = new RegisteredContracts();
        contracts.client = mock(ContractClient.class);
        when(contracts.client.metadata("weather-tools", "weather")).thenReturn(mapper.readTree(
                "{\"version\":\"1\",\"globalId\":40,\"state\":\"ENABLED\"}"));
        when(contracts.client.metadata("default", "summarizer")).thenReturn(mapper.readTree(
                "{\"version\":\"2\",\"globalId\":41,\"state\":\"ENABLED\"}"));
        when(contracts.client.metadata("default", "translator")).thenReturn(mapper.readTree(
                "{\"version\":\"3\",\"globalId\":42,\"state\":\"DEPRECATED\"}"));
        assertEquals(List.of(
                new RegisteredContracts.Contract("weather-tools", "weather", "1", 40, "ENABLED"),
                new RegisteredContracts.Contract("default", "summarizer", "2", 41, "ENABLED"),
                new RegisteredContracts.Contract("default", "translator", "3", 42, "DEPRECATED")), contracts.observe());
        when(contracts.client.metadata("default", "translator")).thenThrow(new NotFoundException());
        assertThrows(ServiceUnavailableException.class, contracts::observe);
    }
}
