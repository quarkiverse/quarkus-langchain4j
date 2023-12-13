package org.acme.example.openai.images;

import java.io.File;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;

@Path("image")
public class ImageModelResource {

    private final ImageModel model;

    public ImageModelResource(ImageModel model) {
        this.model = model;
    }

    @GET
    @Produces("image/png")
    public File generate() {
        Image image = model.generate("A green, walkable, liveable and modern city").content();
        return new File(image.url());
    }
}
