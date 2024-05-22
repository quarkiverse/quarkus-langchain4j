package org.acme.example.openai.images;

import java.io.File;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;

@Path("image")
public class ImageResource {

    private final ImageModel imageModel;
    private final ChatLanguageModel chatLanguageModel;

    public ImageResource(ImageModel model, ChatLanguageModel chatLanguageModel) {
        this.imageModel = model;
        this.chatLanguageModel = chatLanguageModel;
    }

    @GET
    @Produces("image/png")
    @Path("generate")
    public File generate() {
        Image image = imageModel.generate("A green, walkable, liveable and modern city").content();
        return new File(image.url());
    }

    @GET
    @Path("describe")
    public String describe() {
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see?"),
                ImageContent.from("https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png"));
        Response<AiMessage> response = chatLanguageModel.generate(userMessage);
        return response.content().text();
    }
}
