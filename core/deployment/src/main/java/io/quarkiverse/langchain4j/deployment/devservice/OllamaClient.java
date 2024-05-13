package io.quarkiverse.langchain4j.deployment.devservice;

import java.util.List;
import java.util.concurrent.Flow;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface OllamaClient {

    static OllamaClient create(Options options) {
        return new JdkOllamaClient(options);
    }

    /**
     * Returns a list of all models the server has
     */
    List<ModelInfo> localModels();

    /**
     * Get information from a model that already exists in the Ollama server
     *
     * @throws ModelNotFoundException if the server has not previously pulled the model
     */
    ModelInfo modelInfo(String modelName);

    /**
     * Instructs Ollama to pull the specified model. The result here a Publisher in order to facilitate providing updates
     * about the progress of the pull (which can take a long time).
     */
    Flow.Publisher<PullAsyncLine> pullAsync(String modelName);

    record ModelInfo(String name, @JsonProperty("modelfile") String modelFile, String parameters, Details details) {

        public record Details(String family, String parameterSize) {

        }
    }

    record PullAsyncLine(String status, Long total, Long completed) {

    }

    class ModelNotFoundException extends RuntimeException {
        public ModelNotFoundException(String model) {
            super("Model not found: " + model);
        }
    }

    class ModelDoesNotExistException extends RuntimeException {
        public ModelDoesNotExistException(String model) {
            super("Model does not exist: " + model);
        }
    }

    class ServerUnavailableException extends RuntimeException {
        public ServerUnavailableException(String host, int port) {
            super("Ollama server at [" + host + ":" + port + "] is unreachable");
        }
    }

    record Options(String host, int port) {

    }
}
