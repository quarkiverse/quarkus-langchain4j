package io.quarkiverse.langchain4j.ollama.deployment.devservices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;
import org.testcontainers.containers.BindMode;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.devservices.common.ConfigureUtil;

/**
 * Testcontainers implementation for Ollama model.
 * <p>
 * Supported image: {@code ollama/ollama:latest}
 * Find more info about Ollama container on <a href=
 * "https://ollama.ai/blog/ollama-is-now-available-as-an-official-docker-image">https://ollama.ai/blog/ollama-is-now-available-as-an-official-docker-image</a>.
 * <p>
 * Exposed ports: 11434 (Rest API)
 */
public class OllamaContainer extends org.testcontainers.ollama.OllamaContainer {
    private static final Logger LOG = Logger.getLogger(OllamaContainer.class);
    public static final String CONFIG_OLLAMA_PORT = OllamaDevServicesProcessor.FEATURE + ".ollama.port";
    public static final String CONFIG_OLLAMA_HTTP_SERVER = OllamaDevServicesProcessor.FEATURE + ".ollama.host";
    public static final String CONFIG_OLLAMA_ENDPOINT = OllamaDevServicesProcessor.FEATURE + ".ollama.endpoint";
    public static final int DEFAULT_OLLAMA_PORT = 11434;
    private static final String DEFAULT_OLLAMA_DIRECTORY = "/root/.ollama";

    private final boolean useSharedNetwork;

    /**
     * The dynamic host name determined from TestContainers.
     */
    private String hostName;

    OllamaContainer(OllamaDevServicesBuildConfig config, boolean useSharedNetwork) {
        super(DockerImageName.parse(config.imageName()).asCompatibleSubstituteFor("ollama/ollama"));
        this.useSharedNetwork = useSharedNetwork;

        super.withLabel(OllamaDevServicesProcessor.DEV_SERVICE_LABEL, OllamaDevServicesProcessor.PROVIDER)
                .withStartupTimeout(Duration.ofMinutes(1));

        var localOllamaDir = Paths.get(System.getProperty("user.home"), ".ollama").normalize();
        createLocalOllamaDirIfNeeded(localOllamaDir);

        if (Files.isDirectory(localOllamaDir)) {
            try {
                super.withFileSystemBind(localOllamaDir.toRealPath().toString(), DEFAULT_OLLAMA_DIRECTORY, BindMode.READ_WRITE);
            } catch (IOException e) {
                // Eat it and continue on without binding the directory
                LOG.warnf(
                        "Not able to bind Ollama directory %s to %s because of %s. This means that downloaded models may not be shared with the Ollama client.",
                        localOllamaDir.toString(), DEFAULT_OLLAMA_DIRECTORY, e.getMessage());
            }
        }
    }

    private static void createLocalOllamaDirIfNeeded(Path localOllamaDir) {
        if (!Files.isDirectory(localOllamaDir)) {
            try {
                Files.createDirectories(localOllamaDir);
            } catch (IOException e) {
                // Eat it and continue on without binding the directory
                LOG.warnf("Not able to create Ollama directory %s because of %s", localOllamaDir.toString(), e.getMessage());
            }
        }
    }

    @Override
    protected void configure() {
        super.configure();

        if (useSharedNetwork) {
            hostName = ConfigureUtil.configureSharedNetwork(this, OllamaDevServicesProcessor.PROVIDER);
        }
    }

    /**
     * Info about the DevService.
     *
     * @return the map of as running configuration of the dev service
     */
    public Map<String, String> getExposedConfig() {
        var host = getHost();
        var port = getPort();
        var exposed = new HashMap<String, String>(3);

        exposed.put(CONFIG_OLLAMA_PORT, Objects.toString(port));
        exposed.put(CONFIG_OLLAMA_HTTP_SERVER, host);
        exposed.put(CONFIG_OLLAMA_ENDPOINT, getEndpoint());
        exposed.putAll(getEnvMap());

        return exposed;
    }

    public int getPort() {
        return getMappedPort(DEFAULT_OLLAMA_PORT);
    }

    @Override
    public String getHost() {
        return ((this.hostName != null) && !this.hostName.isEmpty()) ? this.hostName : super.getHost();
    }
}
