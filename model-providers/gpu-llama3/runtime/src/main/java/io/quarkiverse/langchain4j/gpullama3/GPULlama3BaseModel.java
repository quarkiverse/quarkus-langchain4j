package io.quarkiverse.langchain4j.gpullama3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import org.beehive.gpullama3.auxiliary.LastRunMetrics;
import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.format.ChatFormat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

abstract class GPULlama3BaseModel {

    /**
     * Centralized holder of the actual model instance.
     * *Shared* across ChatModel and StreamingChatModel instances.
     * Lazily initialized by ensureInitialized() when first doChat() is called.
     */
    GPULlama3ModelHolder holder;

    public Model getModel() {
        return holder.model;
    }

    public Sampler getSampler() {
        return holder.sampler;
    }

    // @formatter:off
    public String modelResponse(ChatRequest request, IntConsumer tokenConsumer) {
        List<Integer> promptTokens = new ArrayList<>();

        if (holder.model.shouldAddBeginOfText()) {
            promptTokens.add(holder.chatFormat.getBeginOfText());
        }

        processPromptMessages(request.messages(), promptTokens);

        Set<Integer> stopTokens = holder.chatFormat.getStopTokens();
        List<Integer> responseTokens;

        if (holder.onGPU) {
            responseTokens = holder.model.generateTokensGPU(
                    holder.state,
                    0,
                    promptTokens.subList(0, promptTokens.size()),
                    stopTokens,
                    holder.maxTokens,
                    holder.sampler,
                    false,
                    tokenConsumer,
                    holder.tornadoVMPlan);
        } else {
            responseTokens = holder.model.generateTokens(
                    holder.state,
                    0,
                    promptTokens.subList(0, promptTokens.size()),
                    stopTokens,
                    holder.maxTokens,
                    holder.sampler,
                    false,
                    tokenConsumer);
        }

        Integer stopToken = null;
        if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.getLast())) {
            stopToken = responseTokens.getLast();
            responseTokens.removeLast();
        }

        String responseText = holder.model.tokenizer().decode(responseTokens);

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

    private void processPromptMessages(List<ChatMessage> messageList, List<Integer> promptTokens) {
        for (ChatMessage msg : messageList) {
            if (msg instanceof UserMessage userMessage) {
                promptTokens.addAll(holder.chatFormat.encodeMessage(
                        new ChatFormat.Message(ChatFormat.Role.USER, userMessage.singleText())));
            } else if (msg instanceof SystemMessage systemMessage && holder.model.shouldAddSystemPrompt()) {
                promptTokens.addAll(
                        holder.chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, systemMessage.text())));
            } else if (msg instanceof AiMessage aiMessage) {
                promptTokens.addAll(
                        holder.chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, aiMessage.text())));
            }
        }

        // EncodeHeader to prime the model to start generating a new assistant response.
        promptTokens.addAll(holder.chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));
    }
}
