package io.quarkiverse.langchain4j.ollama.devservices;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.LazyFuture;

import com.github.dockerjava.api.command.InspectContainerResponse;

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
public class OllamaContainer extends GenericContainer<OllamaContainer> {

    public static final String CONFIG_OLLAMA_PORT = OllamaProcessor.FEATURE + ".ollama.port";
    public static final String CONFIG_OLLAMA_HTTP_SERVER = OllamaProcessor.FEATURE + ".ollama.http.server";

    /**
     * Logger which will be used to capture container STDOUT and STDERR.
     */
    private static final Logger log = Logger.getLogger(OllamaContainer.class);
    /**
     * Default Ollama Port.
     */
    private static final Integer PORT_OLLAMA = 11434;

    private final DockerImageName dockerImageName;
    private final OllamaConfig config;
    private final String localOllamaImage;
    private final boolean useSharedNetwork;

    /**
     * The dynamic host name determined from TestContainers.
     */
    private String hostName;

    private String runtimeModelId;

    OllamaContainer(OllamaConfig config, String localOllamaImage, boolean useSharedNetwork, LazyFuture<DockerImageName> image,
            String runtimeModelId) {
        super(image.get());
        this.config = config;
        this.dockerImageName = image.get();
        this.localOllamaImage = localOllamaImage;
        this.useSharedNetwork = useSharedNetwork;
        this.runtimeModelId = runtimeModelId;
        super.withLabel(OllamaProcessor.DEV_SERVICE_LABEL, OllamaProcessor.FEATURE);
        super.withNetwork(Network.SHARED);

        super.addFixedExposedPort(PORT_OLLAMA, PORT_OLLAMA);
        super.withImagePullPolicy(dockerImageName -> !dockerImageName.asCanonicalNameString().equals(localOllamaImage));
    }

    @Override
    protected void configure() {
        super.configure();

        if (useSharedNetwork) {
            hostName = ConfigureUtil.configureSharedNetwork(this, OllamaProcessor.FEATURE);
        }

    }

    /**
     * Info about the DevService.
     *
     * @return the map of as running configuration of the dev service
     */
    public Map<String, String> getExposedConfig() {
        Map<String, String> exposed = new HashMap<>(2);
        exposed.put(CONFIG_OLLAMA_PORT, Objects.toString(PORT_OLLAMA));
        exposed.put(CONFIG_OLLAMA_HTTP_SERVER, getOllamaHost());
        exposed.putAll(super.getEnvMap());
        return exposed;
    }

    public String getOllamaHost() {
        if (hostName != null && !hostName.isEmpty()) {
            return hostName;
        } else {
            return getHost();
        }
    }

    /**
     * Use "quarkus.langchain4j.ollama.chat-model.model-id" to configure Ollama model.
     *
     * @return the model id configured in langchain4j extension or the one defined in the devservices configuration
     */
    static String getModelId(OllamaConfig config) {
        // first try default port
        String modelId = ConfigProvider.getConfig().getOptionalValue("quarkus.langchain4j.ollama.chat-model.model-id",
                String.class).orElse("");

        // if not found search through named mailers until we find one
        if ("".equals(modelId)) {
            // check for all configs
            for (String key : ConfigProvider.getConfig().getPropertyNames()) {
                if (key.contains("quarkus.langchain4j.ollama.chat-model.") && key.endsWith("model-id")) {
                    modelId = ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse("");
                    if (!"".equals(modelId)) {
                        break;
                    }
                }
            }
        }

        if ("".equals(modelId)) {
            return config.model();
        }

        return modelId;
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        if (!this.dockerImageName.equals(DockerImageName.parse(this.localOllamaImage))) {
            try {
                log.infof("Pulling the '%s' model . This could take several minutes", this.runtimeModelId);
                execInContainer("ollama", "pull", this.runtimeModelId);
                log.info("Pull competed!");
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Error pulling orca-mini model", e);
            }
        }
    }

}
