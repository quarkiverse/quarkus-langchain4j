package io.quarkiverse.langchain4j.sample.registry.orchestrator;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.ServiceUnavailableException;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.ApicurioAgentsRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
class WorkflowResourceTest {
    @InjectMock
    ApicurioAgentsRegistry registry;

    @InjectMock
    WeatherAssistant weather;

    @InjectMock
    DiscoveredWeather discoveredWeather;

    @InjectMock
    RequiredContracts contracts;

    @Test
    void connectsMcpOutputToBothA2AHandoffs() throws Exception {
        UntypedAgent summarizer = mock(UntypedAgent.class, withSettings().extraInterfaces(AgentInstance.class));
        UntypedAgent translator = mock(UntypedAgent.class, withSettings().extraInterfaces(AgentInstance.class));
        when(contracts.check())
                .thenReturn(List.of(new RequiredContracts.Contract("default", "translator", "7", 12, "ENABLED")));
        when(registry.allAgents()).thenReturn(Map.of("summarizer", (AgentInstance) summarizer,
                "translator", (AgentInstance) translator));
        when(discoveredWeather.weatherForCity("Amsterdam")).thenReturn("weather fixture");
        String input = "Prepare a short briefing using only these facts. State that weather is fictional.\n"
                + "Weather: weather fixture\nContext: meeting";
        when(summarizer.invoke(Map.of("input", input))).thenReturn("brief summary");
        when(translator.invoke(Map.of("input", "brief summary"))).thenReturn("résumé");
        RestAssured.given().contentType("application/json").body(Map.of("city", "Amsterdam", "context", "meeting"))
                .post("/workflow/briefing").then().statusCode(200)
                .body("contracts[0].version", equalTo("7"))
                .body("steps[0].output", equalTo("weather fixture"))
                .body("steps[1].input", equalTo(input))
                .body("steps[2].input", equalTo("brief summary"))
                .body("result", equalTo("résumé"));
        var order = inOrder(contracts, registry, discoveredWeather, summarizer, translator);
        order.verify(contracts).check();
        order.verify(registry).allAgents();
        order.verify(discoveredWeather).weatherForCity("Amsterdam");
        order.verify(summarizer).invoke(Map.of("input", input));
        order.verify(translator).invoke(Map.of("input", "brief summary"));
    }

    @Test
    void deprecatedContractBlocksBeforeDiscoveryAndInvocation() {
        doThrow(new ServiceUnavailableException("Translator contract is DEPRECATED")).when(contracts).check();
        RestAssured.given().contentType("application/json").body(Map.of("city", "Amsterdam", "context", "meeting"))
                .post("/workflow/briefing").then().statusCode(503)
                .body("stage", equalTo("contract-check"))
                .body("error", equalTo("Translator contract is DEPRECATED"));
        verifyNoInteractions(registry, discoveredWeather, weather);
    }

    @Test
    void handsTheSummaryToTheDiscoveredTranslator() {
        UntypedAgent summarizer = mock(UntypedAgent.class, withSettings().extraInterfaces(AgentInstance.class));
        UntypedAgent translator = mock(UntypedAgent.class, withSettings().extraInterfaces(AgentInstance.class));
        when(registry.allAgents()).thenReturn(Map.of("summarizer", (AgentInstance) summarizer,
                "translator", (AgentInstance) translator));
        when(summarizer.invoke(Map.of("input", "original text"))).thenReturn("short summary");
        when(translator.invoke(Map.of("input", "short summary"))).thenReturn("résumé court");

        RestAssured.given().contentType("text/plain").body("original text")
                .post("/workflow/summarize-and-translate").then().statusCode(200)
                .body("summary", equalTo("short summary"))
                .body("translation", equalTo("résumé court"));
        verify(translator).invoke(Map.of("input", "short summary"));
        verify(registry).allAgents();
    }

    @Test
    void missingTranslatorFailsBeforeInvokingTheSummarizer() {
        UntypedAgent summarizer = mock(UntypedAgent.class, withSettings().extraInterfaces(AgentInstance.class));
        when(registry.allAgents()).thenReturn(Map.of("summarizer", (AgentInstance) summarizer));
        RestAssured.given().contentType("text/plain").body("original text")
                .post("/workflow/summarize-and-translate").then().statusCode(503);
        verifyNoInteractions(summarizer);
    }

    @Test
    void invalidInputDoesNotReachDiscoveryOrTheModel() {
        for (String input : new String[] { " ", "x".repeat(8001) }) {
            RestAssured.given().contentType("text/plain").body(input)
                    .post("/workflow/summarize-and-translate").then().statusCode(400);
            RestAssured.given().contentType("text/plain").body(input)
                    .post("/workflow/weather").then().statusCode(400);
        }
        verifyNoInteractions(registry, weather);
    }
}
