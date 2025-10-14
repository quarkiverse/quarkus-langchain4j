package io.quarkiverse.langchain4j.gpullama3;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import org.beehive.gpullama3.auxiliary.LastRunMetrics;
import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.inference.state.State;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.format.ChatFormat;
import org.beehive.gpullama3.model.loader.ModelLoader;
import org.beehive.gpullama3.tornadovm.TornadoVMMasterPlan;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

abstract class GPULlama3BaseModel {
    State state;
    List<Integer> promptTokens;
    ChatFormat chatFormat;
    TornadoVMMasterPlan tornadoVMPlan;
    private Integer maxTokens;
    private Boolean onGPU;
    private Model model;
    private Sampler sampler;

    // @formatter:off
    public void init(
            Path modelPath,
            Double temperature,
            Double topP,
            Integer seed,
            Integer maxTokens,
            Boolean onGPU) {
        this.maxTokens = maxTokens;
        this.onGPU = onGPU;

        try {
            this.model = ModelLoader.loadModel(modelPath, maxTokens, true, onGPU);
            this.state = model.createNewState();
            this.sampler = Sampler.selectSampler(
                    model.configuration().vocabularySize(), temperature.floatValue(), topP.floatValue(), seed);
            this.chatFormat = model.chatFormat();
            if (onGPU) {
                tornadoVMPlan = TornadoVMMasterPlan.initializeTornadoVMPlan(state, model);
                // cleanup ?
            } else {
                tornadoVMPlan = null;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model from " + modelPath, e);
        }
    }

    public Model getModel() {
        return model;
    }

    public Sampler getSampler() {
        return sampler;
    }

    public String modelResponse(ChatRequest request, IntConsumer tokenConsumer) {
        this.promptTokens = new ArrayList<>();

        if (model.shouldAddBeginOfText()) {
            promptTokens.add(chatFormat.getBeginOfText());
        }

        processPromptMessages(request.messages());

        Set<Integer> stopTokens = chatFormat.getStopTokens();
        List<Integer> responseTokens;

        if (onGPU) {
            responseTokens = model.generateTokensGPU(
                    state,
                    0,
                    promptTokens.subList(0, promptTokens.size()),
                    stopTokens,
                    maxTokens,
                    sampler,
                    false,
                    tokenConsumer,
                    tornadoVMPlan);
        } else {
            responseTokens = model.generateTokens(
                    state,
                    0,
                    promptTokens.subList(0, promptTokens.size()),
                    stopTokens,
                    maxTokens,
                    sampler,
                    false,
                    tokenConsumer);
        }

        Integer stopToken = null;
        if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.getLast())) {
            stopToken = responseTokens.getLast();
            responseTokens.removeLast();
        }

        String responseText = model.tokenizer().decode(responseTokens);

        // Add the response content tokens to conversation history
        promptTokens.addAll(responseTokens);

        // Add the stop token to complete the message
        if (stopToken != null) {
            promptTokens.add(stopToken);
        }

        if (stopToken == null) {
            return "Ran out of context length...\n Increase context length with by passing to llama-tornado --max-tokens XXX";
        } else {
            return responseText;
        }
    }
    // @formatter:on

    public void printLastMetrics() {
        LastRunMetrics.printMetrics();
    }

    private void processPromptMessages(List<ChatMessage> messageList) {
        for (ChatMessage msg : messageList) {
            if (msg instanceof UserMessage userMessage) {
                promptTokens.addAll(chatFormat.encodeMessage(
                        new ChatFormat.Message(ChatFormat.Role.USER, userMessage.singleText())));
            } else if (msg instanceof SystemMessage systemMessage && model.shouldAddSystemPrompt()) {
                promptTokens.addAll(
                        chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, systemMessage.text())));
            } else if (msg instanceof AiMessage aiMessage) {
                promptTokens.addAll(
                        chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, aiMessage.text())));
            }
        }

        // EncodeHeader to prime the model to start generating a new assistant response.
        promptTokens.addAll(chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));
    }
}
