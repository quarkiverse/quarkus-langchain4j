package io.quarkiverse.langchain4j.sample.fileserver;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FileServer {
    private static final Logger LOG = Logger.getLogger(FileServer.class);

    String folder;

    public FileServer(@ConfigProperty(name = "working.folder", defaultValue = "target") String folder) {
        this.folder = folder;
    }

    @Tool(description = "Read a file")
    String readFileContent(@ToolArg(description = "path to the file") String file) {
        Path allowed = Path.of(folder).toAbsolutePath();
        Path path = Path.of(file).toAbsolutePath();
        if (!path.startsWith(allowed)) {
            throw new IllegalArgumentException(String.format("The file %s is not inside allowed folder %s", file, allowed));
        }
        try (Stream<String> lines = Files.lines(path)) {
            LOG.info("Reading file: " + path);
            return lines.collect(StringBuilder::new,
                    StringBuilder::append,
                    StringBuilder::append).toString();
        } catch (IOException e) {
            throw new RuntimeException("Error reading file " + path + ": "+ e.getMessage());
        }
    }
}
