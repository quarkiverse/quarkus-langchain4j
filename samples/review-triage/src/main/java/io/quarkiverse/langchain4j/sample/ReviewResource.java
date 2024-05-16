package io.quarkiverse.langchain4j.sample;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/review")
public class ReviewResource {

    @Inject
    TriageService triage;

    public record Request(String post) {
    }

    public record Response(boolean sarcasmDetected) {

    }

    @POST
    public Response triage(Request request) {
        return new Response(triage.triage(request.post()));
    }

}
