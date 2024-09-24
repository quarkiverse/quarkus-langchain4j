package org.acme.example.openai.aiservices;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ImageUrl;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("describe-image")
public class DescribeImageResource {

    private final ImageDescriber imageDescriber;

    public DescribeImageResource(ImageDescriber imageDescriber) {
        this.imageDescriber = imageDescriber;
    }

    @POST
    public String generate(String url) {
        return imageDescriber.describe(url);
    }

    @RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @ApplicationScoped
    public interface ImageDescriber {

        @UserMessage("This is image was reported on a GitHub issue. If this is a snippet of Java code, please respond"
                + " with only the Java code. If it is not, respond with 'NOT AN IMAGE'")
        String describe(@ImageUrl String url);
    }
}
