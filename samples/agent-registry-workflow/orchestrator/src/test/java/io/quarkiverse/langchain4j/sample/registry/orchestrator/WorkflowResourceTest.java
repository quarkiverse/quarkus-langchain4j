package io.quarkiverse.langchain4j.sample.registry.orchestrator;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Map;

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
