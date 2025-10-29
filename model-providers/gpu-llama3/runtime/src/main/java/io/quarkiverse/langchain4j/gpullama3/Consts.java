package io.quarkiverse.langchain4j.gpullama3;

public final class Consts {

    private Consts() {
    }

    /**
     * working links:
     * https://huggingface.co/beehive-lab/Llama-3.2-1B-Instruct-GGUF/blob/main/Llama-3.2-1B-Instruct-FP16.gguf
     * https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-f16.gguf
     */

    public static final String DEFAULT_CHAT_MODEL_NAME = "beehive-lab/Llama-3.2-1B-Instruct-GGUF";
    public static final String DEFAULT_CHAT_MODEL_QUANTIZATION = "FP16";

}