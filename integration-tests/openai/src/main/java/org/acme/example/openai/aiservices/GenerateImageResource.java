package org.acme.example.openai.aiservices;

import java.net.URI;
import java.nio.file.Paths;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import dev.langchain4j.data.image.Image;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("generate-image")
public class GenerateImageResource {

    private final ImageGenerator imageGenerator;

    public GenerateImageResource(ImageGenerator imageGenerator) {
        this.imageGenerator = imageGenerator;
    }

    @POST
    @Produces("image/png")
    public java.nio.file.Path generate(String prompt) {
        URI fileUri = imageGenerator.generate(prompt).url(); // this is saved locally because we have quarkus.langchain4j.openai.image-model.persist=true
        return Paths.get(fileUri);
    }

    @RegisterAiService
    @ApplicationScoped
    public interface ImageGenerator {

        Image generate(String prompt);
    }
}
