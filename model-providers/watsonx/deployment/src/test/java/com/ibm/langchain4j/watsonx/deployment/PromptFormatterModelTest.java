package com.ibm.langchain4j.watsonx.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.apache.commons.lang3.stream.Streams;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.GraniteCodePromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.GranitePromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.Llama31PromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.LlamaPromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.MistralLargePromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.MistralPromptFormatter;
import io.quarkiverse.langchain4j.watsonx.prompt.impl.NoopPromptFormatter;
import io.quarkus.test.QuarkusUnitTest;

public class PromptFormatterModelTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Test
    void llama_prompt_formatter() {

        Streams.of(new LlamaPromptFormatter(), new Llama31PromptFormatter())
                .forEach(promptFormatter -> {
                    var prompt1 = List.of(
                            SystemMessage.from("You are a poet"),
                            UserMessage.from("Write a poem about dog of ten lines"));

                    assertEquals("""
                            <|begin_of_text|><|start_header_id|>system<|end_header_id|>

                            You are a poet<|eot_id|><|start_header_id|>user<|end_header_id|>

                            Write a poem about dog of ten lines<|eot_id|><|start_header_id|>assistant<|end_header_id|>

                            """, promptFormatter.format(prompt1));

                    var prompt2 = List.of(
                            SystemMessage.from("You are a poet"),
                            UserMessage.from("Write a poem about dog of ten lines"),
                            AiMessage.from("I'm an assistant"));

                    assertEquals("""
                            <|begin_of_text|><|start_header_id|>system<|end_header_id|>

                            You are a poet<|eot_id|><|start_header_id|>user<|end_header_id|>

                            Write a poem about dog of ten lines<|eot_id|><|start_header_id|>assistant<|end_header_id|>

                            I'm an assistant<|eot_id|>""", promptFormatter.format(prompt2));

                    var prompt3 = List.<ChatMessage> of(UserMessage.from("Write a poem about dog of ten lines"));

                    assertEquals("""
                            <|begin_of_text|><|start_header_id|>user<|end_header_id|>

                            Write a poem about dog of ten lines<|eot_id|><|start_header_id|>assistant<|end_header_id|>

                            """, promptFormatter.format(prompt3));

                    var prompt4 = List.<ChatMessage> of(
                            UserMessage.from("Write a poem about dog of ten lines"),
                            AiMessage.from("I'm an assistant"));

                    assertEquals("""
                            <|begin_of_text|><|start_header_id|>user<|end_header_id|>

                            Write a poem about dog of ten lines<|eot_id|><|start_header_id|>assistant<|end_header_id|>

                            I'm an assistant<|eot_id|>""", promptFormatter.format(prompt4));

                    var prompt5 = List.<ChatMessage> of(
                            SystemMessage.from("You are a poet"),
                            AiMessage.from("I'm an assistant"));

                    assertEquals("""
                            <|begin_of_text|><|start_header_id|>system<|end_header_id|>

                            You are a poet<|eot_id|><|start_header_id|>assistant<|end_header_id|>

                            I'm an assistant<|eot_id|>""", promptFormatter.format(prompt5));

                    var prompt6 = List.of(
                            SystemMessage.from("You are a poet"),
                            UserMessage.from("Write a poem about dog of ten lines"),
                            AiMessage.from("dog dog"),
                            UserMessage.from("Write a poem about cat of ten lines"),
                            AiMessage.from("cat cat"));

                    assertEquals("""
                            <|begin_of_text|><|start_header_id|>system<|end_header_id|>

                            You are a poet<|eot_id|><|start_header_id|>user<|end_header_id|>

                            Write a poem about dog of ten lines<|eot_id|><|start_header_id|>assistant<|end_header_id|>

                            dog dog<|eot_id|><|start_header_id|>user<|end_header_id|>

                            Write a poem about cat of ten lines<|eot_id|><|start_header_id|>assistant<|end_header_id|>

                            cat cat<|eot_id|>""", promptFormatter.format(prompt6));
                });
    }

    @Test
    void mistral_prompt_formatter() {

        Streams.of(new MistralPromptFormatter(), new MistralLargePromptFormatter())
                .forEach(promptFormatter -> {

                    var prompt1 = List.of(
                            SystemMessage.from("You are a poet"),
                            UserMessage.from("Write a poem about dog of ten lines"));

                    assertEquals("<s>[INST] You are a poet [/INST]</s>[INST] Write a poem about dog of ten lines [/INST]",
                            promptFormatter.format(prompt1));

                    var prompt2 = List.of(
                            SystemMessage.from("You are a poet"),
                            UserMessage.from("Write a poem about dog of ten lines"),
                            AiMessage.from("I'm an assistant"));

                    assertEquals(
                            "<s>[INST] You are a poet [/INST]</s>[INST] Write a poem about dog of ten lines [/INST]I'm an assistant</s>",
                            promptFormatter.format(prompt2));

                    var prompt3 = List.<ChatMessage> of(UserMessage.from("Write a poem about dog of ten lines"));
                    assertEquals("<s>[INST] Write a poem about dog of ten lines [/INST]", promptFormatter.format(prompt3));

                    var prompt4 = List.<ChatMessage> of(
                            UserMessage.from("Write a poem about dog of ten lines"),
                            AiMessage.from("I'm an assistant"));

                    assertEquals("<s>[INST] Write a poem about dog of ten lines [/INST]I'm an assistant</s>",
                            promptFormatter.format(prompt4));

                    var prompt5 = List.<ChatMessage> of(
                            SystemMessage.from("You are a poet"),
                            AiMessage.from("I'm an assistant"));

                    assertEquals("<s>[INST] You are a poet [/INST]</s>I'm an assistant</s>",
                            promptFormatter.format(prompt5));

                    var prompt6 = List.of(
                            SystemMessage.from("You are a poet"),
                            UserMessage.from("Write a poem about dog of ten lines"),
                            AiMessage.from("dog dog"),
                            UserMessage.from("Write a poem about cat of ten lines"),
                            AiMessage.from("cat cat"));

                    assertEquals(
                            "<s>[INST] You are a poet [/INST]</s>[INST] Write a poem about dog of ten lines [/INST]dog dog</s>[INST] Write a poem about cat of ten lines [/INST]cat cat</s>",
                            promptFormatter.format(prompt6));
                });
    }

    @Test
    void granite_prompt_formatter() {

        var promptFormatter = new GranitePromptFormatter();
        var prompt1 = List.of(
                SystemMessage.from("You are a poet"),
                UserMessage.from("Write a poem about dog of ten lines"));

        assertEquals("""
                <|system|>
                You are a poet
                <|user|>
                Write a poem about dog of ten lines
                <|assistant|>
                """, promptFormatter.format(prompt1));

        var prompt2 = List.of(
                SystemMessage.from("You are a poet"),
                UserMessage.from("Write a poem about dog of ten lines"),
                AiMessage.from("I'm an assistant"));

        assertEquals("""
                <|system|>
                You are a poet
                <|user|>
                Write a poem about dog of ten lines
                <|assistant|>
                I'm an assistant""", promptFormatter.format(prompt2));

        var prompt3 = List.<ChatMessage> of(UserMessage.from("Write a poem about dog of ten lines"));
        assertEquals("""
                <|user|>
                Write a poem about dog of ten lines
                <|assistant|>
                """, promptFormatter.format(prompt3));

        var prompt4 = List.<ChatMessage> of(
                UserMessage.from("Write a poem about dog of ten lines"),
                AiMessage.from("I'm an assistant"));

        assertEquals("""
                <|user|>
                Write a poem about dog of ten lines
                <|assistant|>
                I'm an assistant""", promptFormatter.format(prompt4));

        var prompt5 = List.<ChatMessage> of(
                SystemMessage.from("You are a poet"),
                AiMessage.from("I'm an assistant"));

        assertEquals("""
                <|system|>
                You are a poet
                <|assistant|>
                I'm an assistant""", promptFormatter.format(prompt5));

        var prompt6 = List.of(
                SystemMessage.from("You are a poet"),
                UserMessage.from("Write a poem about dog of ten lines"),
                AiMessage.from("dog dog"),
                UserMessage.from("Write a poem about cat of ten lines"),
                AiMessage.from("cat cat"));

        assertEquals("""
                <|system|>
                You are a poet
                <|user|>
                Write a poem about dog of ten lines
                <|assistant|>
                dog dog
                <|user|>
                Write a poem about cat of ten lines
                <|assistant|>
                cat cat""", promptFormatter.format(prompt6));
    }

    @Test
    void granite_code_prompt_formatter() {

        var promptFormatter = new GraniteCodePromptFormatter();
        var prompt1 = List.of(
                SystemMessage.from("You are a poet"),
                UserMessage.from("Write a poem about dog of ten lines"));

        assertEquals("""
                System:
                You are a poet

                Question:
                Write a poem about dog of ten lines

                Answer:
                """, promptFormatter.format(prompt1));

        var prompt2 = List.of(
                SystemMessage.from("You are a poet"),
                UserMessage.from("Write a poem about dog of ten lines"),
                AiMessage.from("I'm an assistant"));

        assertEquals("""
                System:
                You are a poet

                Question:
                Write a poem about dog of ten lines

                Answer:
                I'm an assistant
                """, promptFormatter.format(prompt2));

        var prompt3 = List.<ChatMessage> of(UserMessage.from("Write a poem about dog of ten lines"));
        assertEquals("""
                Question:
                Write a poem about dog of ten lines

                Answer:
                """, promptFormatter.format(prompt3));

        var prompt4 = List.<ChatMessage> of(
                UserMessage.from("Write a poem about dog of ten lines"),
                AiMessage.from("I'm an assistant"));

        assertEquals("""
                Question:
                Write a poem about dog of ten lines

                Answer:
                I'm an assistant
                """, promptFormatter.format(prompt4));

        var prompt5 = List.<ChatMessage> of(
                SystemMessage.from("You are a poet"),
                AiMessage.from("I'm an assistant"));

        assertEquals("""
                System:
                You are a poet

                Answer:
                I'm an assistant
                """, promptFormatter.format(prompt5));

        var prompt6 = List.of(
                SystemMessage.from("You are a poet"),
                UserMessage.from("Write a poem about dog of ten lines"),
                AiMessage.from("dog dog"),
                UserMessage.from("Write a poem about cat of ten lines"),
                AiMessage.from("cat cat"));

        assertEquals("""
                System:
                You are a poet

                Question:
                Write a poem about dog of ten lines

                Answer:
                dog dog

                Question:
                Write a poem about cat of ten lines

                Answer:
                cat cat
                """, promptFormatter.format(prompt6));
    }

    @Test
    void default_prompt_formatter() {

        var promptFormatter = new NoopPromptFormatter("\n");
        var promptFormatterWithDifferentJoin = new NoopPromptFormatter("@");

        var prompt1 = List.of(
                SystemMessage.from("You are a poet"),
                UserMessage.from("Write a poem about dog of ten lines"));

        assertEquals("""
                You are a poet
                Write a poem about dog of ten lines""", promptFormatter.format(prompt1));

        assertEquals("You are a poet@Write a poem about dog of ten lines", promptFormatterWithDifferentJoin.format(prompt1));

        var prompt2 = List.of(
                SystemMessage.from("You are a poet"),
                UserMessage.from("Write a poem about dog of ten lines"),
                AiMessage.from("I'm an assistant"));

        assertEquals("""
                You are a poet
                Write a poem about dog of ten lines
                I'm an assistant""", promptFormatter.format(prompt2));

        var prompt3 = List.<ChatMessage> of(UserMessage.from("Write a poem about dog of ten lines"));
        assertEquals("Write a poem about dog of ten lines", promptFormatter.format(prompt3));

        var prompt4 = List.<ChatMessage> of(
                UserMessage.from("Write a poem about dog of ten lines"),
                AiMessage.from("I'm an assistant"));

        assertEquals("""
                Write a poem about dog of ten lines
                I'm an assistant""", promptFormatter.format(prompt4));

        var prompt5 = List.<ChatMessage> of(
                SystemMessage.from("You are a poet"),
                AiMessage.from("I'm an assistant"));

        assertEquals("""
                You are a poet
                I'm an assistant""", promptFormatter.format(prompt5));

        var prompt6 = List.of(
                SystemMessage.from("You are a poet"),
                UserMessage.from("Write a poem about dog of ten lines"),
                AiMessage.from("dog dog"),
                UserMessage.from("Write a poem about cat of ten lines"),
                AiMessage.from("cat cat"));

        assertEquals("""
                You are a poet
                Write a poem about dog of ten lines
                dog dog
                Write a poem about cat of ten lines
                cat cat""", promptFormatter.format(prompt6));
    }
}
