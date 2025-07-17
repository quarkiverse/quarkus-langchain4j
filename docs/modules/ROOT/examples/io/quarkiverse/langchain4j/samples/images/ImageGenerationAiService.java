// tag::head[]
package io.quarkiverse.langchain4j.samples.images;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("You are an AI that generates images from text prompts.")
public interface ImageGenerationAiService {
    // end::head[]

    // tag::generation[]
    @UserMessage("Generate an image of a {subject}.")
    Image generateImage(String subject);
    // end::generation[]

    // tag::head[]
}
// end::head[]
