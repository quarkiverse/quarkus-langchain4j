package io.quarkiverse.langchain4j.samples.fewshots;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/sentiment")
@Produces(MediaType.TEXT_PLAIN)
public class SentimentResource {
    @Inject
    SentimentAiService sentimentService;

    @GET
    public String analyze(@QueryParam("text") String text) {
        return sentimentService.classifySentiment(text);
    }
}