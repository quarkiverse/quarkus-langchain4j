package io.quarkiverse.langchain4j.sample.plantuml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import dev.langchain4j.data.message.AiMessage;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

@ApplicationScoped
public class PlantUMLValidator implements OutputGuardrail {

    private static final String STARTUML = "@startuml";
    private static final String ENDUML = "@enduml";

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String responseText = responseFromLLM.text();
        if (responseText == null || responseText.isBlank() || !responseText.contains(STARTUML) || !responseText.contains(ENDUML)) {
            return failure("Response does not contain PlantUML code!");
        }

        String code = responseText.substring(responseText.indexOf(STARTUML), responseText.lastIndexOf(ENDUML) + ENDUML.length());
        if (isValidPlantUML(code)) {
            return successWith(code);
        }
        return failure("Generated PlantUML code is invalid!");
    }

    public static boolean isValidPlantUML(String plantUMLCode) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            SourceStringReader reader = new SourceStringReader(plantUMLCode);
            reader.outputImage(os, new FileFormatOption(FileFormat.PNG));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
