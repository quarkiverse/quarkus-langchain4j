package io.quarkiverse.langchain4j.sample;

import java.net.URISyntaxException;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/poem")
@Authenticated
public class PoemResource {

	private final PoemAiService aiService;
	
	public PoemResource(PoemAiService aiService) throws URISyntaxException {
		this.aiService = aiService;
	}
	
    @GET
    public String getPoem() {
    	return aiService.writeAPoem();
    }
}
