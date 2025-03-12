package io.quarkiverse.langchain4j.sample.plantuml;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(chatMemoryProviderSupplier = // disable chat memory
        RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface ImageToPlantUMLService {

    @SystemMessage("You are a PlantUML expert. You reverse engineer images into PlantUML code")
    @UserMessage("""
            Create PlantUML code that descirbes the diagram in the image as close as possible. 
            The diagram should accurately display:
            - component
            - connections
            - arrows
            - links
            - notes
            - text
            Use OCR if needed to read text.
            Respond with just plantuml code, without enclosing it in bakcticks etc.
            """)
    @OutputGuardrails(PlantUMLValidator.class)
    String convert(Image image);
}
