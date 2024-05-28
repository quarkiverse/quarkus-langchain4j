package io.quarkiverse.langchain4j.sample;

import java.net.URISyntaxException;

import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.oidc.IdToken;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/poem")
@Authenticated
public class PoemResource {

	private final PoemAiService aiService;
	@Inject
	@IdToken
	JsonWebToken id;
	public PoemResource(PoemAiService aiService) throws URISyntaxException {
		this.aiService = aiService;
	}
	
    @GET
    public String getPoem() {
    	return id.getClaim(Claims.raw_token);
    	//return aiService.writeAPoem();
    }
}
