package io.quarkiverse.langchain4j.sample.registry.orchestrator;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.ApicurioAgentsRegistry;
import io.smallrye.common.annotation.Blocking;

@Path("/workflow")
@Blocking
@Produces(MediaType.APPLICATION_JSON)
public class WorkflowResource {
    @Inject
    ApicurioAgentsRegistry registry;

    @Inject
    WeatherAssistant weather;

    @Inject
    DiscoveredWeather discoveredWeather;

    @Inject
    RequiredContracts contracts;

    @POST
    @Path("/briefing")
    @Consumes(MediaType.APPLICATION_JSON)
    public Briefing briefing(BriefingRequest request) throws Exception {
        if (request == null) {
            throw new BadRequestException("Provide a city and context");
        }
        requireText(request.city());
        requireText(request.context());
        if (!List.of("Amsterdam", "Paris", "Madrid").contains(request.city())) {
            throw new BadRequestException("Choose Amsterdam, Paris or Madrid");
        }
        List<RequiredContracts.Contract> accepted;
        try {
            accepted = contracts.check();
        } catch (ServiceUnavailableException e) {
            throw new ServiceUnavailableException(Response.status(503)
                    .entity(Map.of("error", e.getMessage(), "stage", "contract-check")).build());
        }
        Map<String, AgentInstance> agents = registry.allAgents();
        UntypedAgent summarizer = requireAgent(agents, "summarizer");
        UntypedAgent translator = requireAgent(agents, "translator");
        // All roles and lifecycle checks are resolved before the first MCP/A2A operation.
        String forecast = discoveredWeather.weatherForCity(request.city());
        String summaryInput = "Prepare a short briefing using only these facts. State that weather is fictional.\n"
                + "Weather: " + forecast + "\nContext: " + request.context();
        String summary = summarizer.invoke(Map.of("input", summaryInput)).toString();
        String translation = translator.invoke(Map.of("input", summary)).toString();
        return new Briefing(accepted, List.of(
                new Step("MCP weather", "weather-tools/weather", request.city(), forecast),
                new Step("A2A summarize", "default/summarizer", summaryInput, summary),
                new Step("A2A translate", "default/translator", summary, translation)), translation);
    }

    @GET
    @Path("/agents")
    public Map<String, String> agents() {
        return registry.allAgents().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().description()));
    }

    @POST
    @Path("/summarize-and-translate")
    @Consumes(MediaType.TEXT_PLAIN)
    public WorkflowResult run(String text) {
        requireText(text);
        // Discover once for this request, then pass the summary into the second real A2A call.
        // Names express the workflow's required roles; network locations come exclusively from Registry.
        Map<String, AgentInstance> agents = registry.allAgents();
        UntypedAgent summarizer = requireAgent(agents, "summarizer");
        UntypedAgent translator = requireAgent(agents, "translator");
        String summary = summarizer.invoke(Map.of("input", text)).toString();
        String translation = translator.invoke(Map.of("input", summary)).toString();
        return new WorkflowResult(summary, translation);
    }

    @POST
    @Path("/weather")
    @Consumes(MediaType.TEXT_PLAIN)
    public WeatherResult weather(String question) {
        requireText(question);
        return new WeatherResult(weather.chat(question));
    }

    private static UntypedAgent requireAgent(Map<String, AgentInstance> agents, String name) {
        if (!(agents.get(name) instanceof UntypedAgent agent)) {
            throw new ServiceUnavailableException("Required agent unavailable: " + name);
        }
        return agent;
    }

    private static void requireText(String text) {
        if (text == null || text.isBlank() || text.length() > 8000) {
            throw new BadRequestException("Provide between 1 and 8000 characters");
        }
    }

    public record WorkflowResult(String summary, String translation) {
    }

    public record WeatherResult(String answer) {
    }

    public record BriefingRequest(String city, String context) {
    }

    public record Step(String operation, String artifact, String input, String output) {
    }

    public record Briefing(List<RequiredContracts.Contract> contracts, List<Step> steps, String result) {
    }
}
