package io.quarkiverse.langchain4j.sample.plantuml;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Optional;

import dev.langchain4j.data.image.Image;

public class Images {

    public static Optional<Path> resolve(String fileOrUrl) {
        if (fileOrUrl == null || fileOrUrl.isBlank()) {
            return Optional.empty();
        }

        // Check if it's a valid file path
        Path filePath = Path.of(fileOrUrl);
        if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
            return Optional.of(filePath);
        }

        // Check if it's a valid URL
        try {
            URL url = URI.create(fileOrUrl).toURL();
            return downloadToTemp(url);
        } catch (MalformedURLException e) {
            return Optional.empty(); // Not a valid URL
        }
    }

    public static Optional<Path> downloadToTemp(URL url) {
        try {
            Path tempFile = Files.createTempFile("download", "." + getFileExtension(url.toString()));
            try (var inputStream = url.openStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return Optional.of(tempFile);
        } catch (IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public static Optional<Image> toImage(Path path) {
        try {
            String imageContent = Base64.getEncoder().encodeToString(Files.readAllBytes(path));
            String filename = path.toFile().getName();
            String extension = getFileExtension(filename);
            String mimeType = Optional.ofNullable(Files.probeContentType(path)).orElse("image/" + extension);
            return Optional.of(Image.builder().mimeType(mimeType).base64Data(imageContent).build());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static String getFileExtension(String s) {
        return s.substring(s.lastIndexOf(".") + 1);
    }
}

