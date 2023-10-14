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
package io.quarkiverse.langchain4j.it;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.Delta;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.OpenAiQuarkusRestApi;
import io.quarkiverse.langchain4j.OpenAiRestApiWriterInterceptor;
import io.quarkiverse.langchain4j.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.runtime.LangChain4jRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.OpenAi;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.mutiny.Multi;

@Path("/langchain4j")
@ApplicationScoped
public class Langchain4jResource {

    private final ChatLanguageModel chatLanguageModel;
    private final OpenAiQuarkusRestApi restApi;
    private final String token;

    public Langchain4jResource(ChatLanguageModel chatLanguageModel, LangChain4jRuntimeConfig runtimeConfig)
            throws URISyntaxException {
        this.chatLanguageModel = chatLanguageModel;
        OpenAi openAi = runtimeConfig.chatModel().openAi();
        this.restApi = QuarkusRestClientBuilder.newBuilder()
                .register(OpenAiRestApiWriterInterceptor.class)
                .baseUri(new URI(openAi.baseUrl().orElse("https://api.openai.com/v1/")))
                .build(OpenAiQuarkusRestApi.class);
        this.token = openAi.apiKey().get();

    }

    @GET
    @Path("chat")
    public String chat() {
        return chatLanguageModel.generate("When was the nobel prize for economics first awarded?");
    }

    @GET
    @Path("streaming/client")
    public Multi<String> streamingClient() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .logitBias(Collections.emptyMap())
                .maxTokens(100)
                .user("testing")
                .presencePenalty(0d)
                .frequencyPenalty(0d)
                .addUserMessage("Write a short 1 paragraph funny poem about developers and null-pointers").build();
        return restApi.streamingCreateChatCompletion(request, token)
                .filter(r -> {
                    if (r.choices() != null) {
                        if (r.choices().size() == 1) {
                            Delta delta = r.choices().get(0).delta();
                            if (delta != null) {
                                return delta.content() != null;
                            }
                        }
                    }
                    return false;
                })
                .map(r -> r.choices().get(0).delta().content())
                .filter(Objects::nonNull);
    }

    @GET
    @Path("partial/client")
    public String streamingOutput() throws Exception {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .logitBias(Collections.emptyMap())
                .maxTokens(100)
                .user("testing")
                .presencePenalty(0d)
                .frequencyPenalty(0d)
                .addUserMessage("Write a short 1 paragraph funny poem about javascript frameworks").build();

        CompletableFuture<String> cf = new CompletableFuture<>();
        StringBuilder sb = new StringBuilder();
        new QuarkusOpenAiClient(token).chatCompletion(request)
                .onPartialResponse(r -> {
                    if (r.choices() != null) {
                        if (r.choices().size() == 1) {
                            Delta delta = r.choices().get(0).delta();
                            if (delta != null) {
                                if (delta.content() != null) {
                                    sb.append(delta.content());
                                }
                            }
                        }
                    }
                })
                .onComplete(() -> {
                    cf.complete(sb.toString());
                })
                .onError(cf::completeExceptionally)
                .execute();
        return cf.get();
    }

}
