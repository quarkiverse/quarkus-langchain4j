package io.quarkiverse.langchain4j.watsonx.prompt;

import java.util.HashMap;
import java.util.Map;

import io.quarkiverse.langchain4j.watsonx.prompt.impl.GraniteCodePromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.GranitePromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.Llama31PromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.LlamaPromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.MistralLargePromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.MistralPromptFormatter;

/**
 * Utility class to map the model names to the corresponding {@link PromptFormatter}.
 */
public class PromptFormatterMapper {

    static final Map<String, PromptFormatter> promptFormatters = new HashMap<>();

    static {

        MistralLargePromptFormatter mistralLargePromptFormatter = new MistralLargePromptFormatter();
        promptFormatters.put("mistralai/mistral-large", mistralLargePromptFormatter);

        MistralPromptFormatter mistralPromptFormatter = new MistralPromptFormatter();
        promptFormatters.put("mistralai/mixtral-8x7b-instruct-v01", mistralPromptFormatter);
        promptFormatters.put("sdaia/allam-1-13b-instruct", mistralPromptFormatter);

        Llama31PromptFormatter llama31PromptFormatter = new Llama31PromptFormatter();
        promptFormatters.put("meta-llama/llama-3-405b-instruct", llama31PromptFormatter);
        promptFormatters.put("meta-llama/llama-3-1-70b-instruct", llama31PromptFormatter);

        LlamaPromptFormatter llamaPromptFormatter = new LlamaPromptFormatter();
        promptFormatters.put("meta-llama/llama-3-70b-instruct", llamaPromptFormatter);
        promptFormatters.put("meta-llama/llama-3-8b-instruct", llamaPromptFormatter);
        promptFormatters.put("meta-llama/llama-3-1-8b-instruct", llamaPromptFormatter);

        GranitePromptFormatter granitePromptFormatter = new GranitePromptFormatter();
        promptFormatters.put("ibm/granite-13b-chat-v2", granitePromptFormatter);
        promptFormatters.put("ibm/granite-13b-instruct-v2", granitePromptFormatter);
        promptFormatters.put("ibm/granite-7b-lab", granitePromptFormatter);

        GraniteCodePromptFormatter graniteCodePromptFormatter = new GraniteCodePromptFormatter();
        promptFormatters.put("ibm/granite-20b-code-instruct", graniteCodePromptFormatter);
        promptFormatters.put("ibm/granite-34b-code-instruct", graniteCodePromptFormatter);
        promptFormatters.put("ibm/granite-3b-code-instruct", graniteCodePromptFormatter);
        promptFormatters.put("ibm/granite-8b-code-instruct", graniteCodePromptFormatter);
    }

    /**
     * Retrieves the {@link PromptFormatter} associated with the specified model name.
     *
     * @param model the name of the model whose {@link PromptFormatter} is requested.
     * @return the {@link PromptFormatter} corresponding to the model name, or null if the model is not found.
     */
    public static PromptFormatter get(String model) {
        return promptFormatters.get(model);
    }

    public static boolean toolIsSupported(String model) {
        return switch (model) {
            case "mistralai/mistral-large" -> true;
            case "meta-llama/llama-3-405b-instruct", "meta-llama/llama-3-1-70b-instruct" -> true;
            default -> false;
        };
    }
}
