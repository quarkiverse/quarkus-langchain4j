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

import static org.acme.example.openai.MessageUtil.createChatCompletionRequest;
import static org.acme.example.openai.MessageUtil.createCompletionRequest;
import static org.acme.example.openai.MessageUtil.createEmbeddingRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestStreamElementType;

import dev.ai4j.openai4j.chat.AssistantMessage;
import dev.ai4j.openai4j.chat.ChatCompletionChoice;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.chat.Delta;
import dev.ai4j.openai4j.completion.CompletionChoice;
import dev.ai4j.openai4j.completion.CompletionResponse;
import dev.ai4j.openai4j.embedding.EmbeddingResponse;
import io.quarkiverse.langchain4j.openai.OpenAiRestApi;
import io.quarkiverse.langchain4j.openai.runtime.config.Langchain4jOpenAiConfig;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("restApi")
@ApplicationScoped
public class QuarkusRestApiResource {

    private final OpenAiRestApi restApi;
    private final String token;

    public QuarkusRestApiResource(Langchain4jOpenAiConfig runtimeConfig)
            throws URISyntaxException {
        this.restApi = QuarkusRestClientBuilder.newBuilder()
                .baseUri(new URI(runtimeConfig.baseUrl()))
                .build(OpenAiRestApi.class);
        this.token = runtimeConfig.apiKey()
                .orElseThrow(() -> new IllegalArgumentException("quarkus.langchain4j.openai.api-key must be provided"));

    }

    @GET
    @Path("chat/sync")
    public String chatSync() {
        return restApi.blockingChatCompletion(
                createChatCompletionRequest("Write a short 1 paragraph funny poem about segmentation fault"), token, null)
                .content();
    }

    @GET
    @Path("chat/async")
    public Uni<String> chatAsync() {
        return restApi
                .createChatCompletion(createChatCompletionRequest("Write a short 1 paragraph funny poem about Unicode"), token,
                        null)
                .map(ChatCompletionResponse::content);
    }

    @GET
    @Path("chat/streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> chatStreaming() {
        return restApi.streamingChatCompletion(
                createChatCompletionRequest("Write a short 1 paragraph funny poem about Enterprise Java"), token, null)
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
                createCompletionRequest("Write a short 1 paragraph funny poem about segmentation fault"), token, null).text();
    }

    @GET
    @Path("language/async")
    public Uni<String> languageAsync() {
        return restApi
                .completion(createCompletionRequest("Write a short 1 paragraph funny poem about Unicode"), token, null)
                .map(CompletionResponse::text);
    }

    @GET
    @Path("language/streaming")
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> languageStreaming() {
        return restApi.streamingCompletion(
                createCompletionRequest("Write a short 1 paragraph funny poem about Enterprise Java"), token, null)
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
        return restApi.blockingEmbedding(createEmbeddingRequest("Your text string goes here"), token, null).embedding();
    }

    @GET
    @Path("embedding/async")
    public Uni<List<Float>> embeddingAsync() {
        return restApi.embedding(createEmbeddingRequest("Your text string goes here"), token, null)
                .map(EmbeddingResponse::embedding);
    }

}
