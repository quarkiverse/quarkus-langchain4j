package io.quarkiverse.langchain4j.jlama.runtime.config;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

import io.quarkus.runtime.LaunchMode;

/**
 * Sets {@code quarkus.langchain4j.jlama.models-path} to {@code quarkus-app/jlama} if it exists
 */
public class ModelsPathConfigSource implements ConfigSource {

    private static final String SENTINEL = "#sen-val#";
    public static final String SUPPORTED_PROPERTY_NAME = "quarkus.langchain4j.jlama.models-path";

    private volatile String value = null;

    @Override
    public String getName() {
        return "ModelsPathConfigSource";
    }

    @Override
    public int getOrdinal() {
        // make it overridable by users
        return DEFAULT_ORDINAL;
    }

    @Override
    public Set<String> getPropertyNames() {
        return Set.of(SUPPORTED_PROPERTY_NAME);
    }

    @Override
    public String getValue(String name) {
        if (!SUPPORTED_PROPERTY_NAME.equals(name)) {
            return null;
        }
        if (LaunchMode.current() != LaunchMode.NORMAL) {
            return null;
        }
        String result = value;
        if (result == null) {
            result = value = produceValue();
        }
        if (result.equals(SENTINEL)) {
            return null;
        }
        return result;
    }

    private String produceValue() {
        try {
            Class<?> clazz = Class.forName("io.quarkus.bootstrap.runner.QuarkusEntryPoint", false, Thread.currentThread()
                    .getContextClassLoader());
            String path = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
            if (path == null) {
                return SENTINEL;
            }
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
            Path appRoot = new File(decodedPath).toPath().getParent().getParent().getParent();
            Path jlamaRoot = appRoot.resolve("jlama");
            if (Files.isDirectory(jlamaRoot)) {
                return jlamaRoot.toAbsolutePath().toString();
            }
        } catch (ClassNotFoundException ignored) {

        }
        return SENTINEL;
    }
}
