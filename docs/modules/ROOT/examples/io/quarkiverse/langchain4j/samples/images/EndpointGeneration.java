package io.quarkiverse.langchain4j.samples.images;

import java.io.IOException;
import java.net.URI;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/endpoint")
public class EndpointGeneration {

    @Inject
    ImageGenerationAiService imageGenerationAiService;

    @GET
    @Path("/generate-image")
    @Produces("image/png")
    public byte[] generateImage() {
        var image = imageGenerationAiService.generateImage("a rabbit in a space suit");
        return readBytes(image.url());
    }

    private byte[] readBytes(URI url) {
        try (var is = url.toURL().openStream()) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read image from URL: " + url, e);
        }
    }

}
