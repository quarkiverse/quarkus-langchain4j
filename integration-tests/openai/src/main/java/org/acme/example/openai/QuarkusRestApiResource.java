/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.acme.example.openai;

import static org.acme.example.openai.MessageUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.langchain4j.model.openai.internal.chat.AssistantMessage;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.Delta;
import dev.langchain4j.model.openai.internal.completion.CompletionChoice;
import dev.langchain4j.model.openai.internal.completion.CompletionResponse;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse;
import io.quarkiverse.langchain4j.openai.common.OpenAiRestApi;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("restApi")
@ApplicationScoped
public class QuarkusRestApiResource {

    private final OpenAiRestApi restApi;
    private final String token;
    private final String organizationId;

    public QuarkusRestApiResource(LangChain4jOpenAiConfig runtimeConfig)
            throws URISyntaxException {
        LangChain4jOpenAiConfig.OpenAiConfig openAiConfig = runtimeConfig.defaultConfig();
        this.restApi = QuarkusRestClientBuilder.newBuilder()
                .baseUri(new URI(openAiConfig.baseUrl()))
                .build(OpenAiRestApi.class);
        this.token = openAiConfig.apiKey();
        this.organizationId = openAiConfig.organizationId().orElse(null);
    }

    @GET
    @Path("chat/sync")
    public String chatSync() {
        return restApi.blockingChatCompletion(
                createChatCompletionRequest(
                        "Which one of these languages is more susceptible to segmentation fault: Java, Go, C++ or Rust?"),
                OpenAiRestApi.ApiMetadata.builder()
                        .openAiApiKey(token)
                        .organizationId(organizationId)
                        .build())
                .content();
    }

    @GET
    @Path("chat/async")
    public Uni<String> chatAsync() {
        return restApi
                .createChatCompletion(createChatCompletionRequest("Write a short definition of Unicode"),
                        OpenAiRestApi.ApiMetadata.builder()
                                .openAiApiKey(token)
                                .organizationId(organizationId)
                                .build())
                .map(ChatCompletionResponse::content);
    }

    @GET
    @Path("chat/streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> chatStreaming() {
        return restApi.streamingChatCompletion(
                createChatCompletionRequest("Write a short 1 paragraph funny poem about Enterprise Java"),
                OpenAiRestApi.ApiMetadata.builder()
                        .openAiApiKey(token)
                        .organizationId(organizationId)
                        .build())
                .map(r -> {
                    if (r.choices() != null) {
                        if (r.choices().size() == 1) {
                            ChatCompletionChoice choice = r.choices().get(0);
                            Delta delta = choice.delta();
                            if (delta != null) {
                                if (delta.content() != null) {
                                    return delta.content();
                                }
                            } else { // normally this is not needed but mock APIs don't really work with the streaming response
                                AssistantMessage message = choice.message();
                                if (message != null) {
                                    String content = message.content();
                                    if (content != null) {
                                        return content;
                                    }
                                }
                            }
                        }
                    }
                    return "";
                });
    }

    @GET
    @Path("language/sync")
    public String languageSync() {
        return restApi.blockingCompletion(
                createCompletionRequest(
                        "Which one of these languages is more susceptible to segmentation fault: Java, Go, C++ or Rust?"),
                OpenAiRestApi.ApiMetadata.builder()
                        .openAiApiKey(token)
                        .organizationId(organizationId)
                        .build())
                .text();
    }

    @GET
    @Path("language/async")
    public Uni<String> languageAsync() {
        return restApi
                .completion(createCompletionRequest("Write a short definition of Unicode"),
                        OpenAiRestApi.ApiMetadata.builder()
                                .openAiApiKey(token)
                                .organizationId(organizationId)
                                .build())
                .map(CompletionResponse::text);
    }

    @GET
    @Path("language/streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> languageStreaming() {
        return restApi.streamingCompletion(
                createCompletionRequest("Write a short 1 paragraph funny poem about Enterprise Java"),
                OpenAiRestApi.ApiMetadata.builder()
                        .openAiApiKey(token)
                        .organizationId(organizationId)
                        .build())
                .map(r -> {
                    if (r.choices() != null) {
                        if (r.choices().size() == 1) {
                            CompletionChoice choice = r.choices().get(0);
                            var text = choice.text();
                            if (text != null) {
                                return text;
                            }
                        }
                    }
                    return "";
                });
    }

    @GET
    @Path("embedding/sync")
    public List<Float> embeddingSync() {
        return restApi.blockingEmbedding(createEmbeddingRequest("Your text string goes here"),
                OpenAiRestApi.ApiMetadata.builder()
                        .openAiApiKey(token)
                        .organizationId(organizationId)
                        .build())
                .embedding();
    }

    @GET
    @Path("embedding/async")
    public Uni<List<Float>> embeddingAsync() {
        return restApi
                .embedding(createEmbeddingRequest("Your text string goes here"),
                        OpenAiRestApi.ApiMetadata.builder()
                                .openAiApiKey(token)
                                .organizationId(organizationId)
                                .build())
                .map(EmbeddingResponse::embedding);
    }

}
