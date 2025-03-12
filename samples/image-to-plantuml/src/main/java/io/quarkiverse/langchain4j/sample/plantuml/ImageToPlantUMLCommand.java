package io.quarkiverse.langchain4j.sample.plantuml;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

import dev.langchain4j.data.image.Image;
import jakarta.inject.Inject;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "image-to-plantuml", mixinStandardHelpOptions = true, description = "Reverse engineers images to PlantUML diagrams", version = "v1.0")
public class ImageToPlantUMLCommand implements Callable<Integer> {

    @Parameters(paramLabel = "filename", arity = "0..1", description = "Image to convert.")
    String filenameOrUrl;

    @Inject
    ImageToPlantUMLService imageToPlantUML;

    @Override
    public Integer call() {
        Optional<Path> imagePath = Images.resolve(filenameOrUrl);
        Optional<Image> image = imagePath.flatMap(Images::toImage);
        image.ifPresentOrElse(i -> {
            System.out.println("Converting " + filenameOrUrl + " to PlantUML code...");
            String code = imageToPlantUML.convert(i);
            System.out.println(code);
        }, () -> System.err.println("Failed to read image from: " + filenameOrUrl));
        return ExitCode.OK;
    }

}
