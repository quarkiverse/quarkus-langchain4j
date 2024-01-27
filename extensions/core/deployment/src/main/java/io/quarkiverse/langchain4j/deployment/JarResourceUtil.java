package io.quarkiverse.langchain4j.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarResourceUtil {

    public static List<JarEntry> matchingJarEntries(Path jarPath, Predicate<JarEntry> entryPredicate) {
        List<JarEntry> resources = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> e = jarFile.entries();
            while (e.hasMoreElements()) {
                JarEntry jarEntry = e.nextElement();
                if (entryPredicate.test(jarEntry)) {
                    resources.add(jarEntry);
                }

            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return resources;
    }

    public static Path determineJarLocation(Class<?> classFromJar) {
        URL url = classFromJar.getProtectionDomain().getCodeSource().getLocation();
        if (!url.getProtocol().equals("file")) {
            throw new IllegalStateException("Unable to find which jar class " + classFromJar + " belongs to");
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
